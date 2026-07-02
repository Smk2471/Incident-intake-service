package com.example.incident.dto;

import com.example.incident.model.Incident;
import com.example.incident.model.Severity;
import com.example.incident.model.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Slim representation used for list responses. The full event timeline is
 * only returned from GET /incidents/{id} to keep list payloads small and
 * avoid loading history for every row.
 */
public record IncidentSummaryResponse(
        UUID id,
        String title,
        Severity severity,
        String reportedBy,
        String externalReferenceId,
        IncidentStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static IncidentSummaryResponse from(Incident incident) {
        return new IncidentSummaryResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getSeverity(),
                incident.getReportedBy(),
                incident.getExternalReferenceId(),
                incident.getStatus(),
                incident.getCreatedAt(),
                incident.getUpdatedAt()
        );
    }
}
