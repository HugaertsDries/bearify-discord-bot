package com.bearify.music.player.bridge.model;

public record TrackMetadata(
        String title,
        String author,
        String uri,
        long durationMs
) {}
