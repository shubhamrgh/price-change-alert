package com.pricechangealert.service;

import com.pricechangealert.model.UserAccount;
import com.pricechangealert.model.UserSession;
import com.pricechangealert.repository.UserAccountRepository;
import com.pricechangealert.repository.UserSessionRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AuthService {

    public static final String SESSION_COOKIE = "pca_session";
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserAccountRepository users;
    private final UserSessionRepository sessions;
    private final LegacyOwnershipMigrationService legacyOwnership;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    private final SecureRandom random = new SecureRandom();
    private final Duration sessionTtl;

    public AuthService(UserAccountRepository users,
                       UserSessionRepository sessions,
                       LegacyOwnershipMigrationService legacyOwnership,
                       @Value("${price-change-alert.auth.session-ttl:30d}") Duration sessionTtl) {
        this.users = users;
        this.sessions = sessions;
        this.legacyOwnership = legacyOwnership;
        this.sessionTtl = sessionTtl;
    }

    @Transactional
    public UserAccount register(String email, String password, String legacyOwnerId,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        String normalizedEmail = validateEmail(email);
        validatePassword(password);
        if (users.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("An account with that email already exists");
        }
        UserAccount account = new UserAccount();
        account.setId(UUID.randomUUID().toString());
        account.setEmail(normalizedEmail);
        account.setPasswordHash(passwords.encode(password));
        users.save(account);
        legacyOwnership.claim(legacyOwnerId, account.getId());
        createSession(account.getId(), request, response);
        return account;
    }

    @Transactional
    public UserAccount login(String email, String password, String legacyOwnerId,
                             HttpServletRequest request,
                             HttpServletResponse response) {
        String normalizedEmail = validateEmail(email);
        UserAccount account = users.findByEmailIgnoreCase(normalizedEmail)
                .filter(user -> passwords.matches(password == null ? "" : password, user.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"));
        legacyOwnership.claim(legacyOwnerId, account.getId());
        createSession(account.getId(), request, response);
        return account;
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> currentUser(HttpServletRequest request) {
        return rawToken(request)
                .flatMap(token -> sessions.findByTokenHashAndExpiresAtAfter(hash(token), Instant.now()))
                .flatMap(session -> users.findById(session.getUserId()));
    }

    public UserAccount requireUser(HttpServletRequest request) {
        return currentUser(request).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in to continue"));
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        rawToken(request).ifPresent(token -> sessions.deleteByTokenHash(hash(token)));
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO, request).toString());
    }

    @Transactional
    public long deleteExpiredSessions() {
        return sessions.deleteByExpiresAtBefore(Instant.now());
    }

    private void createSession(String userId, HttpServletRequest request, HttpServletResponse response) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setTokenHash(hash(token));
        session.setExpiresAt(Instant.now().plus(sessionTtl));
        sessions.save(session);
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(token, sessionTtl, request).toString());
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge, HttpServletRequest request) {
        return ResponseCookie.from(SESSION_COOKIE, value)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private static Optional<String> rawToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private static String validateEmail(String value) {
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 320 || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        return email;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new IllegalArgumentException("Password must be between 8 and 128 characters");
        }
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
