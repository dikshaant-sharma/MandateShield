package com.mandateshield.controller;

import com.mandateshield.domain.UserDocument;
import com.mandateshield.repository.UserRepository;
import com.mandateshield.service.JwtService;
import com.mandateshield.service.UserSequenceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestController @RequestMapping("/api/v1/auth") @RequiredArgsConstructor
public class AuthController {
    private final UserRepository users;
    private final UserSequenceService sequence;
    private final PasswordEncoder passwords;
    private final JwtService jwt;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Session register(@Valid @RequestBody RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
        UserDocument user = UserDocument.builder().id(sequence.nextUserId()).email(email)
                .fullName(request.fullName().trim()).passwordHash(passwords.encode(request.password())).lastUserActivityDate(Instant.now()).build();
        return session(users.save(user));
    }

    @PostMapping("/login")
    public Session login(@Valid @RequestBody LoginRequest request) {
        UserDocument user = users.findByEmailIgnoreCase(request.email().trim())
                .filter(saved -> passwords.matches(request.password(), saved.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect."));
        return session(user);
    }

    @GetMapping("/me")
    public UserView me(java.security.Principal principal) {
        UserDocument user = users.findById(Long.valueOf(principal.getName())).orElseThrow();
        return view(user);
    }

    private Session session(UserDocument user) { return new Session(jwt.issue(user), view(user)); }
    private UserView view(UserDocument user) { return new UserView(user.getId(), user.getFullName(), user.getEmail()); }
    public record RegisterRequest(@NotBlank @Size(max=80) String fullName, @NotBlank @Email @Size(max=254) String email, @NotBlank @Size(min=8,max=72) String password) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record Session(String token, UserView user) {}
    public record UserView(Long id, String name, String email) {}
}
