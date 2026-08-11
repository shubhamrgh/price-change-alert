package com.pricechangealert.repository;

import com.pricechangealert.model.NotificationChannel;
import com.pricechangealert.model.NotificationPreference;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    List<NotificationPreference> findAllByUserIdOrderByChannel(String userId);
    List<NotificationPreference> findAllByUserIdAndEnabledTrue(String userId);
    Optional<NotificationPreference> findByUserIdAndChannel(String userId, NotificationChannel channel);
}
