package com.auctix.service;

import com.auctix.domain.AuditAction;
import com.auctix.domain.AuditEvent;
import com.auctix.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Append-only audit trail.
 *
 * <p>Each write runs in its own transaction so a rejected bid is still recorded even though the
 * business transaction rolls back.</p>
 *
 * <p>A {@link TransactionTemplate} is used rather than {@code @Transactional(REQUIRES_NEW)} on
 * purpose: with the annotation, the commit happens in the proxy <em>after</em> the method returns,
 * so a failure at flush or commit escapes any try/catch inside the method and would surface as a
 * 500 on an otherwise successful login or bid. Committing inside the template keeps the failure
 * where it can be caught. Auditing must never break the operation it is recording.</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_DETAIL_LENGTH = 1000;

    private final AuditEventRepository repository;
    private final TransactionTemplate transactionTemplate;

    public AuditService(AuditEventRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(Long actorId, String actorEmail, AuditAction action,
                       String entityType, Long entityId, String details) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                    repository.save(new AuditEvent(actorId, truncate(actorEmail, 180), action,
                            truncate(entityType, 40), entityId, truncate(details, MAX_DETAIL_LENGTH))));
        } catch (RuntimeException ex) {
            log.warn("Could not write audit event {} for {} {}: {}",
                    action, entityType, entityId, ex.getMessage());
        }
    }

    public void recordSystem(AuditAction action, String entityType, Long entityId, String details) {
        record(null, "system", action, entityType, entityId, details);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
