package org.example.chessserver.service;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chessserver.dto.GoogleLoginRequest;
import org.example.chessserver.dto.LoginRequest;
import org.example.chessserver.dto.MessageResponse;
import org.example.chessserver.dto.RegisterRequest;
import org.example.chessserver.dto.TokenResponse;
import org.example.chessserver.dto.VerifyOtpRequest;
import org.example.chessserver.dto.ForgotPasswordRequest;
import org.example.chessserver.dto.ResetPasswordRequest;
import org.example.chessserver.entity.User;
import org.example.chessserver.repository.UserRepository;
import org.example.chessserver.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;

    @Value("${google.client.id}")
    private String googleClientId;

    private final Map<String, PendingUser> pendingRegistrations = new ConcurrentHashMap<>();
    private final Map<String, PendingReset> pendingResets = new ConcurrentHashMap<>();

    private static class PendingUser {
        private final RegisterRequest registerRequest;
        private final String otp;
        private final long expiryTime;

        public PendingUser(RegisterRequest registerRequest, String otp, long expiryTime) {
            this.registerRequest = registerRequest;
            this.otp = otp;
            this.expiryTime = expiryTime;
        }

        public RegisterRequest getRegisterRequest() {
            return registerRequest;
        }

        public String getOtp() {
            return otp;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    private static class PendingReset {
        private final String otp;
        private final long expiryTime;

        public PendingReset(String otp, long expiryTime) {
            this.otp = otp;
            this.expiryTime = expiryTime;
        }

        public String getOtp() {
            return otp;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    private String generateOtp() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    public ResponseEntity<?> register(RegisterRequest request) {
        try {
            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                throw new RuntimeException("Tên tài khoản không được để trống");
            }
            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                throw new RuntimeException("Email không được để trống");
            }
            if (request.getPassword() == null || request.getPassword().isEmpty()) {
                throw new RuntimeException("Mật khẩu không được để trống");
            }

            String email = request.getEmail().trim();
            String username = request.getUsername().trim();

            if (userRepository.findByEmail(email).isPresent()) {
                throw new RuntimeException("Email đã tồn tại");
            }
            if (userRepository.findByUsername(username).isPresent()) {
                throw new RuntimeException("Tên tài khoản đã tồn tại");
            }

            // Generate OTP
            String otp = generateOtp();
            long expiryTime = System.currentTimeMillis() + 5 * 60 * 1000; // 5 minutes

            PendingUser pendingUser = new PendingUser(request, otp, expiryTime);
            pendingRegistrations.put(email, pendingUser);

            // Send Email
            emailService.sendVerificationCode(email, otp);

            return ResponseEntity.ok(new MessageResponse("Vui lòng kiểm tra email để nhận mã xác thực (OTP)"));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Đăng ký thất bại: " + e.getMessage());
        }
    }

    public ResponseEntity<?> verifyOtp(VerifyOtpRequest request) {
        try {
            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                throw new RuntimeException("Email không được để trống");
            }
            if (request.getOtp() == null || request.getOtp().trim().isEmpty()) {
                throw new RuntimeException("Mã OTP không được để trống");
            }

            String email = request.getEmail().trim();
            String otp = request.getOtp().trim();

            PendingUser pendingUser = pendingRegistrations.get(email);
            if (pendingUser == null) {
                throw new RuntimeException("Không tìm thấy yêu cầu đăng ký cho email này hoặc yêu cầu đã quá hạn");
            }

            if (pendingUser.isExpired()) {
                pendingRegistrations.remove(email);
                throw new RuntimeException("Mã OTP đã hết hạn, vui lòng đăng ký lại");
            }

            if (!pendingUser.getOtp().equals(otp)) {
                throw new RuntimeException("Mã OTP không chính xác");
            }

            // OTP is valid, save user to database
            RegisterRequest regReq = pendingUser.getRegisterRequest();
            User user = new User();
            user.setUsername(regReq.getUsername().trim());
            user.setEmail(regReq.getEmail().trim());
            user.setPassword(passwordEncoder.encode(regReq.getPassword()));
            user.setCountryCode(regReq.getCountryCode());

            userRepository.save(user);
            pendingRegistrations.remove(email);

            return ResponseEntity.ok(new MessageResponse("Xác thực OTP thành công. Tài khoản đã được tạo."));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Xác thực OTP thất bại: " + e.getMessage());
        }
    }

    public ResponseEntity<?> login(LoginRequest request) {
        try {
            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                throw new RuntimeException("Email hoặc tên tài khoản không được để trống");
            }
            if (request.getPassword() == null || request.getPassword().isEmpty()) {
                throw new RuntimeException("Mật khẩu không được để trống");
            }

            String identifier = request.getEmail().trim();
            User user = userRepository.findByEmail(identifier)
                     .orElseGet(() -> userRepository.findByUsername(identifier)
                             .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại, vui lòng đăng ký!")));

            if (Boolean.TRUE.equals(user.getIsBanned())) {
                throw new RuntimeException("Tài khoản của bạn đã bị khóa");
            }

            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new RuntimeException("Mật khẩu không chính xác");
            }

            String accessToken = jwtUtil.generateToken(
                    user.getEmail(),
                    user.getUserId(),
                    user.getRole()
            );

            String refreshToken = refreshTokenService.createRefreshToken(
                    user.getUserId(),
                    user.getEmail()
            );

            ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                    .httpOnly(true)
                    .secure(false)
                    .path("/")
                    .maxAge(7 * 24 * 60 * 60)
                    .sameSite("Lax")
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(new TokenResponse(accessToken));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Đăng nhập thất bại: " + e.getMessage());
        }
    }

    public ResponseEntity<?> forgotPassword(ForgotPasswordRequest request) {
        try {
            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                throw new RuntimeException("Email không được để trống");
            }

            String email = request.getEmail().trim();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Email không tồn tại trong hệ thống"));

            // Generate OTP
            String otp = generateOtp();
            long expiryTime = System.currentTimeMillis() + 5 * 60 * 1000; // 5 minutes

            pendingResets.put(email, new PendingReset(otp, expiryTime));

            // Send Email
            emailService.sendResetPasswordCode(email, otp);

            return ResponseEntity.ok(new MessageResponse("Mã khôi phục mật khẩu đã được gửi đến email của bạn."));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Yêu cầu khôi phục mật khẩu thất bại: " + e.getMessage());
        }
    }

    public ResponseEntity<?> resetPassword(ResetPasswordRequest request) {
        try {
            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                throw new RuntimeException("Email không được để trống");
            }
            if (request.getOtp() == null || request.getOtp().trim().isEmpty()) {
                throw new RuntimeException("Mã OTP không được để trống");
            }
            if (request.getNewPassword() == null || request.getNewPassword().isEmpty()) {
                throw new RuntimeException("Mật khẩu mới không được để trống");
            }

            String email = request.getEmail().trim();
            String otp = request.getOtp().trim();

            PendingReset pendingReset = pendingResets.get(email);
            if (pendingReset == null) {
                throw new RuntimeException("Không tìm thấy yêu cầu đặt lại mật khẩu cho email này hoặc yêu cầu đã quá hạn");
            }

            if (pendingReset.isExpired()) {
                pendingResets.remove(email);
                throw new RuntimeException("Mã OTP đã hết hạn, vui lòng yêu cầu gửi lại");
            }

            if (!pendingReset.getOtp().equals(otp)) {
                throw new RuntimeException("Mã OTP không chính xác");
            }

            // Reset password
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng cho email này"));

            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(user);

            pendingResets.remove(email);

            return ResponseEntity.ok(new MessageResponse("Đặt lại mật khẩu thành công. Bạn có thể đăng nhập bằng mật khẩu mới."));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Đặt lại mật khẩu thất bại: " + e.getMessage());
        }
    }




    public ResponseEntity<?> loginWithGoogle(GoogleLoginRequest request) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(request.getIdToken());

            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();
                String email = payload.getEmail();
                String name = (String) payload.get("name");
                String locale = (String) payload.get("locale");

                String countryCode;
                if (locale != null && locale.contains("-")) {
                    countryCode = locale.split("-")[1].toUpperCase();
                } else {
                    countryCode = "VN";
                }

                User user = userRepository.findByEmail(email).orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setUsername(name);
                    newUser.setPassword(passwordEncoder.encode("EXTERNAL_AUTH_GOOGLE_" + Math.random()));
                    newUser.setCountryCode(countryCode);
                    return userRepository.save(newUser);
                });

                if (Boolean.TRUE.equals(user.getIsBanned())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponse("Tài khoản của bạn đã bị khóa"));
                }

                String accessToken = jwtUtil.generateToken(
                        user.getEmail(),
                        user.getUserId(),
                        user.getRole()
                );

                String refreshToken = refreshTokenService.createRefreshToken(
                        user.getUserId(),
                        user.getEmail()
                );

                ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                        .httpOnly(true)
                        .secure(false)
                        .path("/")
                        .maxAge(7 * 24 * 60 * 60)
                        .sameSite("Lax")
                        .build();

                return ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE, cookie.toString())
                        .body(new TokenResponse(accessToken));


            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Invalid Google Token"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("Internal Server Error during Google Auth"));
        }
    }
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        String token = jwtUtil.resolveToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Unauthorized"));
        }

        String email = jwtUtil.getEmailFromToken(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "User not found"
        ));
        return ResponseEntity.ok(user);
    }

    public ResponseEntity<?> makeAdmin(Map<String, String> body) {
        String email = body.get("email");
        String secretCode = body.get("secretCode");
        
        if (!"Thang#2006".equals(secretCode) && !"ADMIN_SECRET_KEY_2026".equals(secretCode) && !"ADMIN_SECRET".equals(secretCode)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponse("Invalid secret code"));
        }
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setRole("ROLE_ADMIN");
        userRepository.save(user);
        return ResponseEntity.ok(new MessageResponse("User promoted to admin successfully"));
    }

    public ResponseEntity<?> createAdmin(Map<String, String> body) {
        String username = body.get("username");
        String email = body.get("email");
        String password = body.get("password");
        String countryCode = body.get("countryCode");
        String secretCode = body.get("secretCode");
        
        if (!"Thang#2006".equals(secretCode) && !"ADMIN_SECRET_KEY_2026".equals(secretCode) && !"ADMIN_SECRET".equals(secretCode)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponse("Invalid secret code"));
        }
        
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new MessageResponse("Email already exists"));
        }
        
        User user = new User();
        user.setUsername(username.trim());
        user.setEmail(email.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setCountryCode(countryCode);
        user.setRole("ROLE_ADMIN");
        
        userRepository.save(user);
        return ResponseEntity.ok(new MessageResponse("Admin account created successfully"));
    }

    public ResponseEntity<?> createBotUser(Map<String, String> body) {
        String username = body.get("username");
        String email = body.get("email");
        String password = body.get("password");
        String countryCode = body.get("countryCode");
        String secretCode = body.get("secretCode");
        
        if (!"Thang#2006".equals(secretCode) && !"ADMIN_SECRET_KEY_2026".equals(secretCode) && !"ADMIN_SECRET".equals(secretCode)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponse("Invalid secret code"));
        }
        
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new MessageResponse("Email already exists"));
        }
        
        User user = new User();
        user.setUsername(username.trim());
        user.setEmail(email.trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setCountryCode(countryCode != null ? countryCode : "VN");
        user.setRole("ROLE_USER");
        
        userRepository.save(user);
        return ResponseEntity.ok(new MessageResponse("Bot account created successfully"));
    }
}
