package com.bearify.music.player.bridge.model;

public record Track(
        String title,
        String author,
        String uri,
        long durationMs
) {}
