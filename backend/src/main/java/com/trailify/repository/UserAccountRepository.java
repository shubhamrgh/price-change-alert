package com.trailify.repository;

import com.trailify.model.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, String> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    Optional<UserAccount> findByGoogleSubject(String googleSubject);
    boolean existsByEmailIgnoreCase(String email);
}
