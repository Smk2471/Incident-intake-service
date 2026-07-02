package com.example.incident.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle states for an incident.
 *
 * Terminal states: CLOSED, REJECTED.
 *
 * The allowed-transition map below is intentionally explicit (rather than
 * "any status can move to any other status") so that invalid lifecycle jumps
 * (e.g. CLOSED -> IN_PROGRESS) are rejected at the API boundary instead of
 * silently corrupting incident history.
 */
public enum IncidentStatus {
    OPEN,
    ACKNOWLEDGED,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    REJECTED;

    private static final Map<IncidentStatus, Set<IncidentStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(IncidentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OPEN, EnumSet.of(ACKNOWLEDGED, REJECTED));
        ALLOWED_TRANSITIONS.put(ACKNOWLEDGED, EnumSet.of(IN_PROGRESS, REJECTED));
        ALLOWED_TRANSITIONS.put(IN_PROGRESS, EnumSet.of(RESOLVED, ACKNOWLEDGED));
        ALLOWED_TRANSITIONS.put(RESOLVED, EnumSet.of(CLOSED, IN_PROGRESS));
        ALLOWED_TRANSITIONS.put(CLOSED, EnumSet.noneOf(IncidentStatus.class));
        ALLOWED_TRANSITIONS.put(REJECTED, EnumSet.noneOf(IncidentStatus.class));
    }

    public boolean canTransitionTo(IncidentStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, EnumSet.noneOf(IncidentStatus.class)).contains(target);
    }

    public Set<IncidentStatus> allowedNextStates() {
        return ALLOWED_TRANSITIONS.getOrDefault(this, EnumSet.noneOf(IncidentStatus.class));
    }
}
