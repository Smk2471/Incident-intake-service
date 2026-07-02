package com.example.incident.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail entry for an incident. This is what lets a caller
 * (or an on-call engineer) answer "what happened to this incident, and when?"
 * without relying solely on the mutable current-state fields on Incident.
 */
@Entity
@Table(name = "incident_events")
public class IncidentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private IncidentEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private IncidentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private IncidentStatus toStatus;

    @Column(length = 1000)
    private String note;

    @Column(length = 200)
    private String actor;

    @Column(nullable = false)
    private Instant occurredAt;

    protected IncidentEvent() {
        // JPA
    }

    public static IncidentEvent created(String actor) {
        IncidentEvent event = new IncidentEvent();
        event.eventType = IncidentEventType.CREATED;
        event.toStatus = IncidentStatus.OPEN;
        event.actor = actor;
        event.note = "Incident created";
        return event;
    }

    public static IncidentEvent statusChanged(IncidentStatus from, IncidentStatus to, String actor, String note) {
        IncidentEvent event = new IncidentEvent();
        event.eventType = IncidentEventType.STATUS_CHANGED;
        event.fromStatus = from;
        event.toStatus = to;
        event.actor = actor;
        event.note = note;
        return event;
    }

    public static IncidentEvent duplicateSubmissionDetected(String externalReferenceId) {
        IncidentEvent event = new IncidentEvent();
        event.eventType = IncidentEventType.DUPLICATE_SUBMISSION_DETECTED;
        event.note = "Re-submission with externalReferenceId=" + externalReferenceId + " matched an existing incident; no new incident created";
        return event;
    }

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public void setIncident(Incident incident) {
        this.incident = incident;
    }

    public UUID getId() {
        return id;
    }

    public Incident getIncident() {
        return incident;
    }

    public IncidentEventType getEventType() {
        return eventType;
    }

    public IncidentStatus getFromStatus() {
        return fromStatus;
    }

    public IncidentStatus getToStatus() {
        return toStatus;
    }

    public String getNote() {
        return note;
    }

    public String getActor() {
        return actor;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
