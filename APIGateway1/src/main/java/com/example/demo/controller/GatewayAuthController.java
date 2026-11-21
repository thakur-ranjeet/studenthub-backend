package com.example.demo.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.AuthRequest;
import com.example.demo.filter.JwtUtil;
import com.example.demo.model.UserCredentials;
import com.example.demo.repository.UserCredentialsRepository;
import com.example.demo.util.ResetSupport;

@RestController
@RequestMapping("/api/v1/auth")
public class GatewayAuthController {

    private final UserCredentialsRepository repository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public GatewayAuthController(UserCredentialsRepository repository, 
                                 JwtUtil jwtUtil, 
                                 PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody AuthRequest authRequest) {
        if (authRequest.getUsername() == null || authRequest.getUsername().isBlank()
                || authRequest.getPassword() == null || authRequest.getPassword().length() < 6) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Username is required and the password must be at least 6 characters"));
        }

        if (repository.findByUsername(authRequest.getUsername()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Username already exists!"));
        }

        // Optional e-mail, used only to send a password reset link
        String email = ResetSupport.normalizeEmail(authRequest.getEmail());
        if (email != null && !ResetSupport.isValidEmail(email)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Please enter a valid e-mail address"));
        }
        if (email != null && repository.findByEmail(email).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "That e-mail address is already used by another account"));
        }

        UserCredentials user = new UserCredentials();
        user.setUsername(authRequest.getUsername());
        user.setEmail(email);

        // SECURITY: never trust a role sent by the client. Every new account is a student.
        // Promote admins directly in the database (see README).
        user.setRole("ROLE_STUDENT");

        String hashedPassword = passwordEncoder.encode(authRequest.getPassword());
        user.setPassword(hashedPassword);

        UserCredentials savedUser = repository.save(user);
        return ResponseEntity.ok(Map.of("message", "User registered successfully with ID: " + savedUser.getId()));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody AuthRequest authRequest) {
        Optional<UserCredentials> userOpt = repository.findByUsername(authRequest.getUsername());

        if (userOpt.isPresent()) {
            UserCredentials user = userOpt.get();

            if (passwordEncoder.matches(authRequest.getPassword(), user.getPassword())) {
                String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
                return ResponseEntity.ok(Map.of("token", token));
            }
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid username or password"));
    }
}
