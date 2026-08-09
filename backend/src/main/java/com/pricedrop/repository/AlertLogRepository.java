package com.pricedrop.repository;

import com.pricedrop.model.AlertLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertLogRepository extends JpaRepository<AlertLog, Long> {
    @Query("select a from AlertLog a where a.ownerId = :ownerId or (:ownerId = 'legacy' and a.ownerId is null) order by a.createdAt desc")
    List<AlertLog> findRecentOwnedBy(@Param("ownerId") String ownerId);

    @Query("select a from AlertLog a where a.id = :id and (a.ownerId = :ownerId or (:ownerId = 'legacy' and a.ownerId is null))")
    Optional<AlertLog> findOwnedById(@Param("id") Long id, @Param("ownerId") String ownerId);
}
