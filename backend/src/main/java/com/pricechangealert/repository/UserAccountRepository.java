package com.pricechangealert.repository;

import com.pricechangealert.model.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, String> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    Optional<UserAccount> findByGoogleSubject(String googleSubject);
    boolean existsByEmailIgnoreCase(String email);
}
