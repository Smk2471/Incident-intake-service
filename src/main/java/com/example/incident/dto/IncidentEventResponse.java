package com.example.incident.dto;

import com.example.incident.model.IncidentEvent;
import com.example.incident.model.IncidentEventType;
import com.example.incident.model.IncidentStatus;

import java.time.Instant;

public record IncidentEventResponse(
        IncidentEventType eventType,
        IncidentStatus fromStatus,
        IncidentStatus toStatus,
        String note,
        String actor,
        Instant occurredAt
) {
    public static IncidentEventResponse from(IncidentEvent event) {
        return new IncidentEventResponse(
                event.getEventType(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getNote(),
                event.getActor(),
                event.getOccurredAt()
        );
    }
}
