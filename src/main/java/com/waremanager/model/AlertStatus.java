package com.waremanager.model;

/**
 * OPEN when raised. The planner can mark it INVESTIGATING or EXPLAINED (F20).
 * The system marks it RESOLVED when the condition disappears (risk falls, or sales are corrected).
 */
public enum AlertStatus {
    OPEN("Open"),
    INVESTIGATING("Investigating"),
    EXPLAINED("Explained"),
    RESOLVED("Resolved");

    private final String label;

    AlertStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
