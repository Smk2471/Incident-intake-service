package com.example.incident.controller;

import com.example.incident.dto.CreateIncidentRequest;
import com.example.incident.dto.IncidentResponse;
import com.example.incident.dto.IncidentSummaryResponse;
import com.example.incident.dto.UpdateStatusRequest;
import com.example.incident.model.Incident;
import com.example.incident.model.IncidentStatus;
import com.example.incident.model.Severity;
import com.example.incident.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Incidents", description = "Intake, retrieval, and lifecycle management for field incident reports")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    /**
     * Intake a new incident. Returns 201 for a genuinely new incident, or
     * 200 if the externalReferenceId matched an existing one (see
     * IncidentService#createIncident for rationale). Either way the caller
     * gets back a full incident representation it can rely on.
     */
    @Operation(summary = "Report a new incident",
            description = "Creates a new incident. If externalReferenceId is supplied and already "
                    + "exists, returns the existing incident (200) instead of creating a duplicate (201).")
    @PostMapping("/incidents")
    public ResponseEntity<IncidentResponse> createIncident(@Valid @RequestBody CreateIncidentRequest request) {
        IncidentService.IncidentCreationResult result = incidentService.createIncident(request);
        IncidentResponse body = IncidentResponse.from(result.incident(), result.duplicate());

        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(body);
    }

    @Operation(summary = "Get a single incident by internal ID",
            description = "Returns the incident along with its full event timeline.")
    @GetMapping("/incidents/{id}")
    public ResponseEntity<IncidentResponse> getIncident(@PathVariable UUID id) {
        Incident incident = incidentService.getIncident(id);
        return ResponseEntity.ok(IncidentResponse.from(incident));
    }

    /**
     * List incidents, optionally filtered by severity and/or status.
     * Supports standard pagination via ?page=&size=&sort=.
     */
    @Operation(summary = "List incidents",
            description = "Optionally filter by severity and/or status. Supports page, size, sort query params.")
    @GetMapping("/incidents")
    public ResponseEntity<Page<IncidentSummaryResponse>> listIncidents(
            @Parameter(description = "Filter by severity: LOW, MEDIUM, HIGH, CRITICAL")
            @RequestParam(required = false) Severity severity,
            @Parameter(description = "Filter by status: OPEN, ACKNOWLEDGED, IN_PROGRESS, RESOLVED, CLOSED, REJECTED")
            @RequestParam(required = false) IncidentStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<Incident> incidents = incidentService.listIncidents(severity, status, pageable);
        return ResponseEntity.ok(incidents.map(IncidentSummaryResponse::from));
    }

    /**
     * Advances (or otherwise changes) an incident's lifecycle status.
     * Transitions are validated against the allowed state machine; invalid
     * moves return 409 CONFLICT with INVALID_STATUS_TRANSITION.
     */
    @Operation(summary = "Change an incident's lifecycle status",
            description = "Validates the requested status against the allowed transition state machine. "
                    + "Invalid transitions return 409 with code INVALID_STATUS_TRANSITION.")
    @PatchMapping("/incidents/{id}/status")
    public ResponseEntity<IncidentResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        Incident updated = incidentService.changeStatus(id, request.getStatus(), request.getActor(), request.getNote());
        return ResponseEntity.ok(IncidentResponse.from(updated));
    }
}
