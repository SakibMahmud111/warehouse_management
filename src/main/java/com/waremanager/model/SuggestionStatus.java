package com.waremanager.model;

/**
 * Life cycle of a reorder suggestion (F3): OPEN -> ORDERED -> RECEIVED.
 * OPEN -> DISMISSED when the planner rejects it; OPEN -> CLOSED when a recalculation finds it is no longer needed.
 */
public enum SuggestionStatus {
    OPEN("Open"),
    ORDERED("Ordered"),
    RECEIVED("Received"),
    DISMISSED("Dismissed"),
    CLOSED("Closed (not needed)");

    private final String label;

    SuggestionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
