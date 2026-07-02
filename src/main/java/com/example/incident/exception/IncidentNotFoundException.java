package com.example.incident.exception;

import java.util.UUID;

public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException(UUID id) {
        super("No incident found with id=" + id);
    }
}
