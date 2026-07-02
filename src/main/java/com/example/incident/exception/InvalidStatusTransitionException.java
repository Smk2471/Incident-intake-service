package com.example.incident.exception;

import com.example.incident.model.IncidentStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(IncidentStatus from, IncidentStatus to) {
        super("Cannot transition incident from " + from + " to " + to
                + ". Allowed next states from " + from + ": " + from.allowedNextStates());
    }
}
