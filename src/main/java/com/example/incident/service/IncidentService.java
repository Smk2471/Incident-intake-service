package com.example.incident.service;

import com.example.incident.dto.CreateIncidentRequest;
import com.example.incident.exception.IncidentNotFoundException;
import com.example.incident.exception.InvalidStatusTransitionException;
import com.example.incident.model.Incident;
import com.example.incident.model.IncidentEvent;
import com.example.incident.model.IncidentStatus;
import com.example.incident.model.Severity;
import com.example.incident.repository.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository incidentRepository;

    public IncidentService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    /**
     * Creates a new incident, or, if an externalReferenceId was supplied and
     * already matches an existing incident, returns that existing incident
     * instead of creating a duplicate.
     *
     * Decision: externalReferenceId is treated as an idempotency key, not a
     * uniqueness constraint that should error on collision. Field-reporting
     * clients (radios, mobile apps with spotty connectivity, upstream
     * monitoring systems) commonly retry a submission after a timeout without
     * knowing whether the first attempt succeeded. Silently creating a
     * second incident for the same real-world event would fragment
     * responder attention across duplicate records; hard-failing the retry
     * with a 409 would push retry/reconciliation logic onto every caller.
     * Returning the original record (200, with a "duplicate: true" flag) is
     * safe to call repeatedly and requires no special-case handling by
     * well-behaved clients.
     *
     * The result pair's boolean indicates whether the returned incident was
     * newly created (false) or matched an existing submission (true).
     */
    @Transactional
    public IncidentCreationResult createIncident(CreateIncidentRequest request) {
        String correlationId = MDC.get("correlationId");

        if (request.getExternalReferenceId() != null && !request.getExternalReferenceId().isBlank()) {
            Optional<Incident> existing = incidentRepository.findByExternalReferenceId(request.getExternalReferenceId());
            if (existing.isPresent()) {
                Incident incident = existing.get();
                incident.addEvent(IncidentEvent.duplicateSubmissionDetected(request.getExternalReferenceId()));
                incidentRepository.save(incident);

                log.warn("event=duplicate_submission_detected incidentId={} externalReferenceId={} correlationId={}",
                        incident.getId(), request.getExternalReferenceId(), correlationId);

                return new IncidentCreationResult(incident, true);
            }
        }

        Incident incident = new Incident(
                request.getTitle(),
                request.getDescription(),
                request.getSeverity(),
                request.getReportedBy(),
                request.getExternalReferenceId()
        );
        incident.addEvent(IncidentEvent.created(request.getReportedBy()));
        Incident saved = incidentRepository.save(incident);

        log.info("event=incident_created incidentId={} severity={} reportedBy={} externalReferenceId={} correlationId={}",
                saved.getId(), saved.getSeverity(), saved.getReportedBy(), saved.getExternalReferenceId(), correlationId);

        return new IncidentCreationResult(saved, false);
    }

    @Transactional(readOnly = true)
    public Incident getIncident(UUID id) {
        return incidentRepository.findWithEventsById(id)
                .orElseThrow(() -> {
                    log.info("event=incident_lookup_miss incidentId={} correlationId={}", id, MDC.get("correlationId"));
                    return new IncidentNotFoundException(id);
                });
    }

    @Transactional(readOnly = true)
    public Page<Incident> listIncidents(Severity severity, IncidentStatus status, Pageable pageable) {
        log.info("event=incident_list severityFilter={} statusFilter={} page={} size={} correlationId={}",
                severity, status, pageable.getPageNumber(), pageable.getPageSize(), MDC.get("correlationId"));

        if (severity != null && status != null) {
            return incidentRepository.findBySeverityAndStatus(severity, status, pageable);
        }
        if (severity != null) {
            return incidentRepository.findBySeverity(severity, pageable);
        }
        if (status != null) {
            return incidentRepository.findByStatus(status, pageable);
        }
        return incidentRepository.findAll(pageable);
    }

    /**
     * Transitions an incident's status, validating the move against the
     * IncidentStatus state machine and recording a STATUS_CHANGED event.
     */
    @Transactional
    public Incident changeStatus(UUID id, IncidentStatus targetStatus, String actor, String note) {
        Incident incident = getIncident(id);
        IncidentStatus currentStatus = incident.getStatus();
        String correlationId = MDC.get("correlationId");

        if (currentStatus == targetStatus) {
            log.info("event=status_no_op incidentId={} status={} correlationId={}", id, currentStatus, correlationId);
            return incident;
        }

        if (!currentStatus.canTransitionTo(targetStatus)) {
            log.warn("event=invalid_status_transition_attempt incidentId={} from={} to={} correlationId={}",
                    id, currentStatus, targetStatus, correlationId);
            throw new InvalidStatusTransitionException(currentStatus, targetStatus);
        }

        incident.setStatus(targetStatus);
        incident.addEvent(IncidentEvent.statusChanged(currentStatus, targetStatus, actor, note));
        Incident saved = incidentRepository.save(incident);

        log.info("event=incident_status_changed incidentId={} from={} to={} actor={} correlationId={}",
                id, currentStatus, targetStatus, actor, correlationId);

        return saved;
    }

    public record IncidentCreationResult(Incident incident, boolean duplicate) {
    }
}
