package com.trailify.repository;

import com.trailify.model.AuthToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
    Optional<AuthToken> findByTokenHashAndTypeAndExpiresAtAfter(
            String tokenHash, AuthToken.Type type, Instant now);
    long deleteByEmailIgnoreCaseAndType(String email, AuthToken.Type type);
    long deleteByExpiresAtBefore(Instant now);
}
