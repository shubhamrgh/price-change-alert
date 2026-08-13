package com.pricechangealert.repository;

import com.pricechangealert.model.PasskeyChallenge;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasskeyChallengeRepository extends JpaRepository<PasskeyChallenge, String> {
    long deleteByExpiresAtBefore(Instant now);
}
