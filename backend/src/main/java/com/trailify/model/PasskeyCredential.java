package com.trailify.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "passkey_credentials", indexes = {
        @Index(name = "idx_passkeys_credential", columnList = "credential_id", unique = true),
        @Index(name = "idx_passkeys_user", columnList = "user_id")
})
public class PasskeyCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "credential_id", nullable = false, unique = true, length = 1024)
    private String credentialId;

    @Column(name = "public_key_cose", nullable = false, length = 4096)
    private String publicKeyCose;

    @Column(nullable = false)
    private int algorithm;

    @Column(name = "sign_count", nullable = false)
    private long signCount;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    public String getPublicKeyCose() { return publicKeyCose; }
    public void setPublicKeyCose(String publicKeyCose) { this.publicKeyCose = publicKeyCose; }
    public int getAlgorithm() { return algorithm; }
    public void setAlgorithm(int algorithm) { this.algorithm = algorithm; }
    public long getSignCount() { return signCount; }
    public void setSignCount(long signCount) { this.signCount = signCount; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
