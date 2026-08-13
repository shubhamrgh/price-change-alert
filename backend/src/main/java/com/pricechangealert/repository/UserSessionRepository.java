package com.pricechangealert.repository;

import com.pricechangealert.model.UserSession;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);
    void deleteByTokenHash(String tokenHash);
    long deleteByUserId(String userId);
    long deleteByExpiresAtBefore(Instant now);
}
