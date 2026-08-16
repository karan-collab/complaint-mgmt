package com.societycare.complaint;

/**
 * Why a resident withdrew a complaint. A closed set so the reasons can be
 * counted; free text goes in deletion_comments alongside it.
 */
public enum DeletionReason {
    RESOLVED_ITSELF("Resolved on its own"),
    RAISED_BY_MISTAKE("Raised by mistake"),
    DUPLICATE("Duplicate of another complaint"),
    HANDLED_PRIVATELY("Handled privately"),
    OTHER("Other");

    private final String label;

    DeletionReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
