package com.example.incident.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IncidentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createIncident_happyPath_returns201WithBody() throws Exception {
        Map<String, Object> request = Map.of(
                "title", "Flooding at north warehouse",
                "severity", "CRITICAL",
                "reportedBy", "site-manager-4"
        );

        mockMvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andExpect(jsonPath("$.severity", is("CRITICAL")))
                .andExpect(jsonPath("$.duplicate", is(false)))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void createIncident_missingRequiredFields_returns400WithFieldErrors() throws Exception {
        Map<String, Object> request = Map.of("description", "no title, no severity, no reporter");

        mockMvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void createIncident_invalidSeverityEnum_returns400() throws Exception {
        String badJson = "{\"title\":\"x\",\"severity\":\"NOT_A_REAL_SEVERITY\",\"reportedBy\":\"y\"}";

        mockMvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("MALFORMED_REQUEST_BODY")));
    }

    @Test
    void createIncident_duplicateExternalReferenceId_returns200NotDuplicateRecord() throws Exception {
        Map<String, Object> request = Map.of(
                "title", "Gas leak reported",
                "severity", "HIGH",
                "reportedBy", "dispatcher-9",
                "externalReferenceId", "cad-ticket-55231"
        );

        String firstResponse = mockMvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String firstId = objectMapper.readTree(firstResponse).get("id").asText();

        mockMvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate", is(true)))
                .andExpect(jsonPath("$.id", is(firstId)));
    }

    @Test
    void getIncident_unknownId_returns404WithMachineReadableCode() throws Exception {
        mockMvc.perform(get("/incidents/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("INCIDENT_NOT_FOUND")))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void fullLifecycle_createThenAcknowledgeThenReject() throws Exception {
        Map<String, Object> request = Map.of(
                "title", "Suspicious badge access after hours",
                "severity", "MEDIUM",
                "reportedBy", "security-desk-2"
        );

        String createResponse = mockMvc.perform(post("/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(patch("/incidents/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACKNOWLEDGED\",\"actor\":\"oncall-3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACKNOWLEDGED")))
                .andExpect(jsonPath("$.events.length()", is(2)));

        mockMvc.perform(patch("/incidents/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"actor\":\"oncall-3\",\"note\":\"false alarm, badge test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REJECTED")));

        // Terminal state: further transitions are rejected with 409
        mockMvc.perform(patch("/incidents/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("INVALID_STATUS_TRANSITION")));
    }

    @Test
    void listIncidents_filterBySeverity_onlyReturnsMatching() throws Exception {
        mockMvc.perform(post("/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Minor leak", "severity", "LOW", "reportedBy", "tech-1"))));
        mockMvc.perform(post("/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Building fire", "severity", "CRITICAL", "reportedBy", "tech-2"))));

        mockMvc.perform(get("/incidents").param("severity", "CRITICAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", is(1)))
                .andExpect(jsonPath("$.content[0].severity", is("CRITICAL")));
    }

    @Test
    void everyErrorResponse_includesCorrelationId() throws Exception {
        mockMvc.perform(get("/incidents/{id}", UUID.randomUUID())
                        .header("X-Correlation-Id", "test-corr-123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.correlationId", is("test-corr-123")))
                .andExpect(status().isNotFound());
    }
}
