package com.pricechangealert.service;

import com.pricechangealert.model.AuthToken;
import com.pricechangealert.repository.AuthTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthTokenService {

    private final AuthTokenRepository tokens;
    private final SecureRandom random = new SecureRandom();

    public AuthTokenService(AuthTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Transactional
    public String create(String email, AuthToken.Type type, Duration ttl) {
        tokens.deleteByEmailIgnoreCaseAndType(email, type);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AuthToken token = new AuthToken();
        token.setEmail(email);
        token.setType(type);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now().plus(ttl));
        tokens.save(token);
        return raw;
    }

    @Transactional
    public Optional<String> consume(String raw, AuthToken.Type type) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        Optional<AuthToken> token = tokens.findByTokenHashAndTypeAndExpiresAtAfter(
                hash(raw), type, Instant.now());
        token.ifPresent(tokens::delete);
        return token.map(AuthToken::getEmail);
    }

    @Transactional
    public long deleteExpired() {
        return tokens.deleteByExpiresAtBefore(Instant.now());
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
