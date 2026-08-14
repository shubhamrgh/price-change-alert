package com.trailify.repository;

import com.trailify.model.PasskeyCredential;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredential, Long> {
    Optional<PasskeyCredential> findByCredentialId(String credentialId);
    List<PasskeyCredential> findAllByUserIdOrderByCreatedAtAsc(String userId);
}
