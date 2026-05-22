package com.societycare.complaint;

/**
 * Closed set of complaint categories. Mirrors the CATEGORIES array in
 * scripts/storage.js so backend and frontend share the same vocabulary.
 */
public enum Category {
    PLUMBER("Plumber"),
    CARPENTER("Carpenter"),
    ELECTRICIAN("Electrician"),
    PAINTING("Painting"),
    OTHERS("Others");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
