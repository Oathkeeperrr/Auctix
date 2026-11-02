package com.auctix.repository;

import com.auctix.domain.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    Page<Item> findBySellerId(Long sellerId, Pageable pageable);

    long countBySellerId(Long sellerId);

    /**
     * Drives per-lot seed idempotency. The demo catalogue keys on title, so a lot already in
     * the database is skipped and a newly added one is created, without wiping the volume.
     */
    boolean existsByTitleIgnoreCase(String title);

    /** Used by the seed backfill to find a lot that predates its artwork. */
    Optional<Item> findFirstByTitleIgnoreCase(String title);
}
