package com.auctix.repository;

import com.auctix.domain.AuditAction;
import com.auctix.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Page<AuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AuditEvent> findByActionOrderByCreatedAtDesc(AuditAction action, Pageable pageable);

    List<AuditEvent> findTop50ByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);

    @Query("select a.action, count(a) from AuditEvent a group by a.action order by count(a) desc")
    List<Object[]> aggregateCountsByAction();

    long countByAction(AuditAction action);
}
