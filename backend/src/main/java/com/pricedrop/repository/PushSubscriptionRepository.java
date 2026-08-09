package com.pricedrop.repository;

import com.pricedrop.model.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    Optional<PushSubscription> findByEndpoint(String endpoint);

    void deleteByEndpoint(String endpoint);

    @Query("select p from PushSubscription p where p.ownerId = :ownerId or (:ownerId = 'legacy' and p.ownerId is null)")
    List<PushSubscription> findAllOwnedBy(@Param("ownerId") String ownerId);
}
