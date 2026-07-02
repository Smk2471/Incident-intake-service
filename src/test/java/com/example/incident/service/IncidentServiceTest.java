package com.example.incident.service;

import com.example.incident.dto.CreateIncidentRequest;
import com.example.incident.exception.IncidentNotFoundException;
import com.example.incident.exception.InvalidStatusTransitionException;
import com.example.incident.model.Incident;
import com.example.incident.model.IncidentStatus;
import com.example.incident.model.Severity;
import com.example.incident.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Uses a real (H2 in-memory) repository rather than mocks: the behavior
 * under test -- unique constraint on externalReferenceId, idempotent
 * duplicate handling, and event persistence -- depends on actual database
 * semantics, so mocking the repository would give false confidence.
 */
@SpringBootTest
@Transactional
class IncidentServiceTest {

    @org.springframework.beans.factory.annotation.Autowired
    private IncidentService incidentService;

    @org.springframework.beans.factory.annotation.Autowired
    private IncidentRepository incidentRepository;

    private CreateIncidentRequest baseRequest;

    @BeforeEach
    void setUp() {
        baseRequest = new CreateIncidentRequest();
        baseRequest.setTitle("Transformer fire at substation 4");
        baseRequest.setDescription("Visible smoke, no injuries reported yet");
        baseRequest.setSeverity(Severity.HIGH);
        baseRequest.setReportedBy("field-agent-217");
    }

    @Test
    void createIncident_persistsIncidentWithOpenStatusAndCreatedEvent() {
        IncidentService.IncidentCreationResult result = incidentService.createIncident(baseRequest);

        assertThat(result.duplicate()).isFalse();
        Incident incident = result.incident();
        assertThat(incident.getId()).isNotNull();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getEvents()).hasSize(1);
        assertThat(incident.getEvents().get(0).getNote()).contains("created");
    }

    @Test
    void createIncident_withoutExternalReferenceId_neverCollides() {
        IncidentService.IncidentCreationResult first = incidentService.createIncident(baseRequest);
        IncidentService.IncidentCreationResult second = incidentService.createIncident(baseRequest);

        assertThat(first.incident().getId()).isNotEqualTo(second.incident().getId());
        assertThat(second.duplicate()).isFalse();
    }

    @Test
    void createIncident_resubmittedExternalReferenceId_returnsExistingIncidentInstead() {
        baseRequest.setExternalReferenceId("scada-alert-9981");

        IncidentService.IncidentCreationResult first = incidentService.createIncident(baseRequest);

        CreateIncidentRequest retry = new CreateIncidentRequest();
        retry.setTitle("Duplicate submission of same alert");
        retry.setSeverity(Severity.HIGH);
        retry.setReportedBy("field-agent-217");
        retry.setExternalReferenceId("scada-alert-9981");

        IncidentService.IncidentCreationResult second = incidentService.createIncident(retry);

        assertThat(second.duplicate()).isTrue();
        assertThat(second.incident().getId()).isEqualTo(first.incident().getId());
        // Original title is preserved -- the retry does not overwrite the existing record
        assertThat(second.incident().getTitle()).isEqualTo("Transformer fire at substation 4");

        assertThat(incidentRepository.count()).isEqualTo(1);
    }

    @Test
    void getIncident_unknownId_throwsNotFound() {
        UUID randomId = UUID.randomUUID();
        assertThatThrownBy(() -> incidentService.getIncident(randomId))
                .isInstanceOf(IncidentNotFoundException.class);
    }

    @Test
    void changeStatus_validTransition_updatesStatusAndRecordsEvent() {
        Incident incident = incidentService.createIncident(baseRequest).incident();

        Incident updated = incidentService.changeStatus(incident.getId(), IncidentStatus.ACKNOWLEDGED, "oncall-1", "picked up");

        assertEquals(IncidentStatus.ACKNOWLEDGED, updated.getStatus());
        assertThat(updated.getEvents()).hasSize(2);
        assertThat(updated.getEvents().get(1).getFromStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(updated.getEvents().get(1).getToStatus()).isEqualTo(IncidentStatus.ACKNOWLEDGED);
    }

    @Test
    void changeStatus_skipsIllegalJump_throwsConflict() {
        Incident incident = incidentService.createIncident(baseRequest).incident();

        // OPEN -> RESOLVED is not an allowed direct transition
        assertThatThrownBy(() -> incidentService.changeStatus(incident.getId(), IncidentStatus.RESOLVED, "oncall-1", null))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void changeStatus_fromTerminalState_isRejected() {
        Incident incident = incidentService.createIncident(baseRequest).incident();
        incidentService.changeStatus(incident.getId(), IncidentStatus.REJECTED, "oncall-1", "false alarm");

        assertThatThrownBy(() -> incidentService.changeStatus(incident.getId(), IncidentStatus.OPEN, "oncall-1", null))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void changeStatus_sameStatus_isNoOpAndDoesNotAddEvent() {
        Incident incident = incidentService.createIncident(baseRequest).incident();
        int eventsBefore = incident.getEvents().size();

        Incident result = incidentService.changeStatus(incident.getId(), IncidentStatus.OPEN, "oncall-1", null);

        assertThat(result.getEvents()).hasSize(eventsBefore);
    }
}
