package com.example.incident.dto;

import com.example.incident.model.Incident;
import com.example.incident.model.Severity;
import com.example.incident.model.IncidentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record IncidentResponse(
        UUID id,
        String title,
        String description,
        Severity severity,
        String reportedBy,
        String externalReferenceId,
        IncidentStatus status,
        Instant createdAt,
        Instant updatedAt,
        boolean duplicate,
        List<IncidentEventResponse> events
) {
    public static IncidentResponse from(Incident incident, boolean duplicate) {
        return new IncidentResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getSeverity(),
                incident.getReportedBy(),
                incident.getExternalReferenceId(),
                incident.getStatus(),
                incident.getCreatedAt(),
                incident.getUpdatedAt(),
                duplicate,
                incident.getEvents().stream().map(IncidentEventResponse::from).collect(Collectors.toList())
        );
    }

    public static IncidentResponse from(Incident incident) {
        return from(incident, false);
    }
}
