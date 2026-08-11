package com.pricechangealert.repository;

import com.pricechangealert.model.NotificationDelivery;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    @Query("""
            select d.id from NotificationDelivery d
            where d.nextAttemptAt <= :now
              and (d.status = :pending or d.status = :processing)
            order by d.createdAt asc
            """)
    List<Long> findDispatchableIds(@Param("now") Instant now,
                                   @Param("pending") NotificationDelivery.Status pending,
                                   @Param("processing") NotificationDelivery.Status processing,
                                   Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from NotificationDelivery d where d.id = :id")
    Optional<NotificationDelivery> findByIdForUpdate(@Param("id") Long id);
}
