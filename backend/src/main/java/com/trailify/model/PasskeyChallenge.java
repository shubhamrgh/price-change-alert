package com.trailify.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "passkey_challenges", indexes = {
        @Index(name = "idx_passkey_challenges_expiry", columnList = "expires_at")
})
public class PasskeyChallenge {

    public enum Purpose { REGISTER, LOGIN }

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 16)
    private String purpose;

    @Column(name = "challenge_hash", nullable = false, length = 64)
    private String challengeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getChallengeHash() { return challengeHash; }
    public void setChallengeHash(String challengeHash) { this.challengeHash = challengeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
