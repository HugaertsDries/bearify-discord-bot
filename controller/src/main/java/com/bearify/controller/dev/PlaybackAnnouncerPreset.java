package com.bearify.controller.dev;

public enum PlaybackAnnouncerPreset {
    BROADCAST,
    JUST_SKIPPED,
    HEAVY_QUEUE;

    public static PlaybackAnnouncerPreset from(String value) {
        if (value == null || value.isBlank()) {
            return BROADCAST;
        }
        return switch (value.trim().replace('-', '_').replace(' ', '_').toUpperCase()) {
            case "JUST_SKIPPED" -> JUST_SKIPPED;
            case "HEAVY_QUEUE" -> HEAVY_QUEUE;
            default -> BROADCAST;
        };
    }

    public String optionValue() {
        return name().toLowerCase().replace('_', '-');
    }
}
