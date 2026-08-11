package com.pricechangealert.web;

import com.pricechangealert.model.UserAccount;
import com.pricechangealert.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record Credentials(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @Size(max = 64) String legacyOwnerId) {
    }

    public record AccountResponse(String id, String email, Instant createdAt) {
        static AccountResponse from(UserAccount account) {
            return new AccountResponse(account.getId(), account.getEmail(), account.getCreatedAt());
        }
    }

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse register(@Valid @RequestBody Credentials credentials,
                                    HttpServletRequest request, HttpServletResponse response) {
        return AccountResponse.from(authService.register(
                credentials.email(), credentials.password(), credentials.legacyOwnerId(), request, response));
    }

    @PostMapping("/login")
    public AccountResponse login(@Valid @RequestBody Credentials credentials,
                                 HttpServletRequest request, HttpServletResponse response) {
        return AccountResponse.from(authService.login(
                credentials.email(), credentials.password(), credentials.legacyOwnerId(), request, response));
    }

    @GetMapping("/me")
    public AccountResponse me(HttpServletRequest request) {
        return AccountResponse.from(authService.requireUser(request));
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return Map.of("ok", true);
    }
}
