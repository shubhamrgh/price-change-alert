package com.pricechangealert.web;

import com.pricechangealert.model.UserAccount;
import com.pricechangealert.service.AuthService;
import com.pricechangealert.service.PasskeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record Credentials(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @Size(max = 64) String legacyOwnerId) { }
    public record EmailRequest(@NotBlank @Email @Size(max = 320) String email) { }
    public record TokenRequest(@NotBlank @Size(max = 512) String token,
                               @Size(max = 64) String legacyOwnerId) { }
    public record PasswordReset(@NotBlank @Size(max = 512) String token,
                                @NotBlank @Size(min = 8, max = 128) String password) { }
    public record GoogleRequest(@NotBlank @Size(max = 8192) String credential,
                                @Size(max = 64) String legacyOwnerId) { }
    public record AuthConfig(boolean google, String googleClientId,
                             boolean emailLinks, boolean passkeys) { }

    public record AccountResponse(String id, String email, Instant createdAt) {
        static AccountResponse from(UserAccount account) {
            return new AccountResponse(account.getId(), account.getEmail(), account.getCreatedAt());
        }
    }

    private final AuthService authService;
    private final PasskeyService passkeys;

    public AuthController(AuthService authService, PasskeyService passkeys) {
        this.authService = authService;
        this.passkeys = passkeys;
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

    @GetMapping("/config")
    public AuthConfig config() {
        return new AuthConfig(authService.googleAvailable(), authService.googleClientId(),
                authService.emailAuthAvailable(), passkeys.available());
    }

    @PostMapping("/magic-link/request")
    public Map<String, String> requestMagicLink(@Valid @RequestBody EmailRequest body) {
        authService.requestMagicLink(body.email());
        return Map.of("message", "If that address can receive sign-in mail, a link has been sent.");
    }

    @PostMapping("/magic-link/consume")
    public AccountResponse consumeMagicLink(@Valid @RequestBody TokenRequest body,
                                            HttpServletRequest request, HttpServletResponse response) {
        return AccountResponse.from(authService.consumeMagicLink(
                body.token(), body.legacyOwnerId(), request, response));
    }

    @PostMapping("/password-reset/request")
    public Map<String, String> requestPasswordReset(@Valid @RequestBody EmailRequest body) {
        authService.requestPasswordReset(body.email());
        return Map.of("message", "If an account exists, a password-reset link has been sent.");
    }

    @PostMapping("/password-reset/consume")
    public Map<String, String> resetPassword(@Valid @RequestBody PasswordReset body) {
        authService.resetPassword(body.token(), body.password());
        return Map.of("message", "Password updated. You can now sign in.");
    }

    @PostMapping("/google")
    public AccountResponse google(@Valid @RequestBody GoogleRequest body,
                                  HttpServletRequest request, HttpServletResponse response) {
        return AccountResponse.from(authService.googleLogin(
                body.credential(), body.legacyOwnerId(), request, response));
    }

    @GetMapping("/passkeys")
    public List<PasskeyService.PasskeyView> passkeys(HttpServletRequest request) {
        return passkeys.list(authService.requireUser(request).getId());
    }

    @PostMapping("/passkeys/register/options")
    public Map<String, Object> passkeyRegistrationOptions(HttpServletRequest request) {
        return passkeys.registrationOptions(authService.requireUser(request));
    }

    @PostMapping("/passkeys/register/finish")
    @ResponseStatus(HttpStatus.CREATED)
    public PasskeyService.PasskeyView finishPasskeyRegistration(
            @RequestBody PasskeyService.RegistrationFinish body, HttpServletRequest request) {
        return passkeys.finishRegistration(authService.requireUser(request), body);
    }

    @PostMapping("/passkeys/login/options")
    public Map<String, Object> passkeyLoginOptions(@Valid @RequestBody EmailRequest body) {
        return passkeys.loginOptions(body.email());
    }

    @PostMapping("/passkeys/login/finish")
    public AccountResponse finishPasskeyLogin(@RequestBody PasskeyService.LoginFinish body,
                                              HttpServletRequest request,
                                              HttpServletResponse response,
                                              @RequestParam(required = false) String legacyOwnerId) {
        UserAccount account = passkeys.finishLogin(body);
        authService.establishSession(account, legacyOwnerId, request, response);
        return AccountResponse.from(account);
    }

    @DeleteMapping("/passkeys/{id}")
    public Map<String, Boolean> deletePasskey(@PathVariable Long id, HttpServletRequest request) {
        passkeys.delete(authService.requireUser(request).getId(), id);
        return Map.of("ok", true);
    }
}
