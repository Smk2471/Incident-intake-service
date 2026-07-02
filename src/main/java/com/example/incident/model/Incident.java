package com.example.incident.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core incident record.
 *
 * "id" is the internal system identifier (UUID, always present, never
 * chosen by the caller). "externalReferenceId" is an optional caller-supplied
 * identifier used purely for idempotent intake -- see IncidentService for the
 * duplicate-handling rationale.
 */
@Entity
@Table(
        name = "incidents",
        uniqueConstraints = @UniqueConstraint(name = "uk_external_reference_id", columnNames = "external_reference_id")
)
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Column(nullable = false, length = 200)
    private String reportedBy;

    @Column(name = "external_reference_id", length = 200)
    private String externalReferenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status = IncidentStatus.OPEN;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredAt ASC")
    private List<IncidentEvent> events = new ArrayList<>();

    protected Incident() {
        // JPA
    }

    public Incident(String title, String description, Severity severity, String reportedBy, String externalReferenceId) {
        this.title = title;
        this.description = description;
        this.severity = severity;
        this.reportedBy = reportedBy;
        this.externalReferenceId = externalReferenceId;
        this.status = IncidentStatus.OPEN;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void addEvent(IncidentEvent event) {
        event.setIncident(this);
        this.events.add(event);
    }

    // -- getters / setters --

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getReportedBy() {
        return reportedBy;
    }

    public void setReportedBy(String reportedBy) {
        this.reportedBy = reportedBy;
    }

    public String getExternalReferenceId() {
        return externalReferenceId;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<IncidentEvent> getEvents() {
        return events;
    }
}
