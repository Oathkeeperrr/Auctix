package com.auctix.dto.admin;

import com.auctix.domain.AuditEvent;

import java.time.Instant;

public record AuditEventDto(
        Long id,
        Long actorId,
        String actorEmail,
        String action,
        String entityType,
        Long entityId,
        String details,
        Instant createdAt
) {
    public static AuditEventDto from(AuditEvent event) {
        return new AuditEventDto(
                event.getId(),
                event.getActorId(),
                event.getActorEmail(),
                event.getAction().name(),
                event.getEntityType(),
                event.getEntityId(),
                event.getDetails(),
                event.getCreatedAt());
    }
}
