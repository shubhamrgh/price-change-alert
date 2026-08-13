package com.pricechangealert.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "app_users", indexes = {
        @Index(name = "idx_app_users_email", columnList = "email", unique = true),
        @Index(name = "idx_app_users_google_subject", columnList = "google_subject", unique = true)
})
public class UserAccount {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "google_subject", unique = true, length = 255)
    private String googleSubject;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getGoogleSubject() { return googleSubject; }
    public void setGoogleSubject(String googleSubject) { this.googleSubject = googleSubject; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
