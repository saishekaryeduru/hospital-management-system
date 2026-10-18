package com.saishekar.hospital.auth;

import com.saishekar.hospital.common.ApiException;
import com.saishekar.hospital.model.User;
import com.saishekar.hospital.repository.UserRepository;
import com.saishekar.hospital.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder,
                       AuthenticationManager authManager, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.authManager = authManager;
        this.jwt = jwt;
    }

    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest req) {
        if (users.existsByEmail(req.email())) {
            throw ApiException.conflict("An account with this email already exists");
        }
        var user = users.save(new User(req.email(), encoder.encode(req.password()), req.displayName(), req.role()));
        return responseFor(user);
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest req) {
        authManager.authenticate(new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        var user = users.findByEmail(req.email()).orElseThrow(() -> ApiException.notFound("User"));
        return responseFor(user);
    }

    private AuthDtos.AuthResponse responseFor(User user) {
        return new AuthDtos.AuthResponse(
                jwt.generateToken(user.getEmail(), user.getRole().name()),
                user.getEmail(), user.getDisplayName(), user.getRole());
    }
}
