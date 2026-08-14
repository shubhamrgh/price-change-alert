package com.trailify.repository;

import com.trailify.model.WatchItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface WatchItemRepository extends JpaRepository<WatchItem, Long> {
    boolean existsByOwnerIdAndSymbolAndMarket(String ownerId, String symbol, com.trailify.model.Market market);

    List<WatchItem> findAllByActiveTrueOrderByIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WatchItem w where w.id = :id")
    Optional<WatchItem> findByIdForUpdate(@Param("id") Long id);

    @Query("select w from WatchItem w where w.ownerId = :ownerId or (:ownerId = 'legacy' and w.ownerId is null) order by w.createdAt")
    List<WatchItem> findAllOwnedBy(@Param("ownerId") String ownerId);

    @Query("select w from WatchItem w where w.id = :id and (w.ownerId = :ownerId or (:ownerId = 'legacy' and w.ownerId is null))")
    Optional<WatchItem> findOwnedById(@Param("id") Long id, @Param("ownerId") String ownerId);
}

