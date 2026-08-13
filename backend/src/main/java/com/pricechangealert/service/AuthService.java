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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class AuthService {

    public static final String SESSION_COOKIE = "pca_session";
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserAccountRepository users;
    private final UserSessionRepository sessions;
    private final LegacyOwnershipMigrationService legacyOwnership;
    private final AuthTokenService authTokens;
    private final AuthMailService authMail;
    private final WebClient webClient;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    private final SecureRandom random = new SecureRandom();
    private final Duration sessionTtl;
    private final Duration magicLinkTtl;
    private final Duration resetTtl;
    private final String baseUrl;
    private final String googleClientId;

    AuthService(UserAccountRepository users,
                UserSessionRepository sessions,
                LegacyOwnershipMigrationService legacyOwnership,
                Duration sessionTtl) {
        this(users, sessions, legacyOwnership, null, null, sessionTtl,
                Duration.ofMinutes(15), Duration.ofMinutes(30), "http://localhost:8080", "");
    }

    @Autowired
    public AuthService(UserAccountRepository users,
                       UserSessionRepository sessions,
                       LegacyOwnershipMigrationService legacyOwnership,
                       AuthTokenService authTokens,
                       AuthMailService authMail,
                       @Value("${price-change-alert.auth.session-ttl:30d}") Duration sessionTtl,
                       @Value("${price-change-alert.auth.magic-link-ttl:15m}") Duration magicLinkTtl,
                       @Value("${price-change-alert.auth.password-reset-ttl:30m}") Duration resetTtl,
                       @Value("${price-change-alert.base-url:http://localhost:8080}") String baseUrl,
                       @Value("${price-change-alert.auth.google.client-id:}") String googleClientId) {
        this.users = users;
        this.sessions = sessions;
        this.legacyOwnership = legacyOwnership;
        this.authTokens = authTokens;
        this.authMail = authMail;
        this.webClient = WebClient.builder().build();
        this.sessionTtl = sessionTtl;
        this.magicLinkTtl = magicLinkTtl;
        this.resetTtl = resetTtl;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.googleClientId = googleClientId == null ? "" : googleClientId.trim();
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
                .filter(user -> user.getPasswordHash() != null
                        && passwords.matches(password == null ? "" : password, user.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"));
        legacyOwnership.claim(legacyOwnerId, account.getId());
        createSession(account.getId(), request, response);
        return account;
    }

    @Transactional
    public void requestMagicLink(String email) {
        String normalizedEmail = validateEmail(email);
        if (authTokens == null || authMail == null || !authMail.available()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Email sign-in is not configured");
        }
        String raw = authTokens.create(normalizedEmail, com.pricechangealert.model.AuthToken.Type.MAGIC_LOGIN,
                magicLinkTtl);
        authMail.sendMagicLink(normalizedEmail, baseUrl + "/?magicToken=" + raw);
    }

    @Transactional
    public UserAccount consumeMagicLink(String rawToken, String legacyOwnerId,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authTokens == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Magic-link sign-in is not configured");
        String email = authTokens.consume(rawToken, com.pricechangealert.model.AuthToken.Type.MAGIC_LOGIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This sign-in link is invalid or expired"));
        UserAccount account = users.findByEmailIgnoreCase(email).orElseGet(() -> {
            UserAccount created = new UserAccount();
            created.setId(UUID.randomUUID().toString());
            created.setEmail(email);
            created.setPasswordHash(unusablePasswordHash());
            return created;
        });
        users.save(account);
        legacyOwnership.claim(legacyOwnerId, account.getId());
        createSession(account.getId(), request, response);
        return account;
    }

    @Transactional
    public void requestPasswordReset(String email) {
        String normalizedEmail = validateEmail(email);
        if (authTokens == null || authMail == null || !authMail.available()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Password reset is not configured");
        }
        if (users.findByEmailIgnoreCase(normalizedEmail).isEmpty()) return;
        String raw = authTokens.create(normalizedEmail, com.pricechangealert.model.AuthToken.Type.PASSWORD_RESET,
                resetTtl);
        authMail.sendPasswordReset(normalizedEmail, baseUrl + "/?resetToken=" + raw);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        validatePassword(newPassword);
        if (authTokens == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Password reset is not configured");
        String email = authTokens.consume(rawToken, com.pricechangealert.model.AuthToken.Type.PASSWORD_RESET)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This password-reset link is invalid or expired"));
        UserAccount account = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This password-reset link is invalid or expired"));
        account.setPasswordHash(passwords.encode(newPassword));
        users.save(account);
        sessions.deleteByUserId(account.getId());
    }

    @Transactional
    public UserAccount googleLogin(String idToken, String legacyOwnerId,
                                   HttpServletRequest request, HttpServletResponse response) {
        if (googleClientId.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Google sign-in is not configured");
        if (idToken == null || idToken.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Google sign-in token is required");
        GoogleIdentity identity;
        try {
            identity = webClient.get().uri(uriBuilder -> uriBuilder
                            .scheme("https").host("oauth2.googleapis.com").path("/tokeninfo")
                            .queryParam("id_token", idToken).build())
                    .retrieve().bodyToMono(GoogleIdentity.class).block(Duration.ofSeconds(8));
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google sign-in could not be verified");
        }
        if (identity == null || !googleClientId.equals(identity.aud())
                || !"true".equalsIgnoreCase(identity.email_verified())
                || identity.email() == null || identity.sub() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google sign-in could not be verified");
        }
        String email = validateEmail(identity.email());
        UserAccount account = users.findByGoogleSubject(identity.sub())
                .or(() -> users.findByEmailIgnoreCase(email))
                .orElseGet(() -> {
                    UserAccount created = new UserAccount();
                    created.setId(UUID.randomUUID().toString());
                    created.setEmail(email);
                    created.setPasswordHash(unusablePasswordHash());
                    return created;
                });
        account.setGoogleSubject(identity.sub());
        users.save(account);
        legacyOwnership.claim(legacyOwnerId, account.getId());
        createSession(account.getId(), request, response);
        return account;
    }

    public boolean googleAvailable() {
        return !googleClientId.isBlank();
    }

    public String googleClientId() {
        return googleAvailable() ? googleClientId : "";
    }

    public boolean emailAuthAvailable() {
        return authMail != null && authMail.available();
    }

    @Transactional
    public void establishSession(UserAccount account, String legacyOwnerId,
                                 HttpServletRequest request, HttpServletResponse response) {
        legacyOwnership.claim(legacyOwnerId, account.getId());
        createSession(account.getId(), request, response);
    }

    private record GoogleIdentity(String aud, String email, String email_verified, String sub) { }

    private String unusablePasswordHash() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return passwords.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
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
