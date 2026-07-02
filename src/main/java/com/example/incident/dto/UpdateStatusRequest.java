package com.example.incident.dto;

import com.example.incident.model.IncidentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UpdateStatusRequest {

    @NotNull(message = "status is required")
    private IncidentStatus status;

    @Size(max = 1000, message = "note must be at most 1000 characters")
    private String note;

    @Size(max = 200, message = "actor must be at most 200 characters")
    private String actor;

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }
}
