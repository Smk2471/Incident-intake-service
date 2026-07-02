package com.example.incident.repository;

import com.example.incident.model.Incident;
import com.example.incident.model.IncidentStatus;
import com.example.incident.model.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    Optional<Incident> findByExternalReferenceId(String externalReferenceId);

    /**
     * Eagerly fetches the event timeline in the same query. Used for the
     * single-incident GET, where callers need full history. The list
     * endpoint deliberately does NOT use this -- it returns summaries only,
     * to avoid loading history for every row in a page.
     */
    @EntityGraph(attributePaths = "events")
    Optional<Incident> findWithEventsById(UUID id);

    Page<Incident> findBySeverity(Severity severity, Pageable pageable);

    Page<Incident> findByStatus(IncidentStatus status, Pageable pageable);

    Page<Incident> findBySeverityAndStatus(Severity severity, IncidentStatus status, Pageable pageable);
}
