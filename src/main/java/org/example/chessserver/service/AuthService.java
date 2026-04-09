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
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

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
                .map(user -> {
                    if (passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                        String token = jwtUtil.generateToken(user.getEmail(), user.getUserId());
                        return ResponseEntity.ok(new TokenResponse(token));
                    } else {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Sai mật khẩu"));
                    }
                })
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Tài khoản không tồn tại, vui lòng đăng ký!")));
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

                String token = jwtUtil.generateToken(user.getEmail(), user.getUserId());
                return ResponseEntity.ok(new TokenResponse(token));

            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Invalid Google Token"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MessageResponse("Internal Server Error during Google Auth"));
        }
    }
}
