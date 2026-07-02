package com.example.incident.dto;

import com.example.incident.model.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateIncidentRequest {

    @NotBlank(message = "title must not be blank")
    @Size(max = 200, message = "title must be at most 200 characters")
    private String title;

    @Size(max = 4000, message = "description must be at most 4000 characters")
    private String description;

    @NotNull(message = "severity is required and must be one of LOW, MEDIUM, HIGH, CRITICAL")
    private Severity severity;

    @NotBlank(message = "reportedBy must not be blank")
    @Size(max = 200, message = "reportedBy must be at most 200 characters")
    private String reportedBy;

    @Size(max = 200, message = "externalReferenceId must be at most 200 characters")
    private String externalReferenceId;

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

    public void setExternalReferenceId(String externalReferenceId) {
        this.externalReferenceId = externalReferenceId;
    }
}
