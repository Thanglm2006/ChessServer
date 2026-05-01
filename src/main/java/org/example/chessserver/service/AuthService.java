package org.example.chessserver.service;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import org.example.chessserver.dto.GoogleLoginRequest;
import org.example.chessserver.dto.LoginRequest;
import org.example.chessserver.dto.MessageResponse;
import org.example.chessserver.dto.RegisterRequest;
import org.example.chessserver.dto.TokenResponse;
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

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    @Value("${google.client.id}")
    private String googleClientId;

    public ResponseEntity<?> register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Email đã tồn tại hoặc dữ liệu không hợp lệ"));
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setCountryCode(request.getCountryCode());

        userRepository.save(user);
        return ResponseEntity.ok(new MessageResponse("Đăng ký thành công"));
    }

    public ResponseEntity<?> login(LoginRequest request) {
    return userRepository.findByEmail(request.getEmail())
            .<ResponseEntity<?>>map(user -> {

                if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(new MessageResponse("Sai mật khẩu"));
                }

                String accessToken = jwtUtil.generateToken(
                        user.getEmail(),
                        user.getUserId()
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

            })
            .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Tài khoản không tồn tại, vui lòng đăng ký!")));
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

                String accessToken = jwtUtil.generateToken(
                        user.getEmail(),
                        user.getUserId()
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
}
