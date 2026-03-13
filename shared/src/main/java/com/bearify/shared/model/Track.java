package com.bearify.shared.model;

public record Track(
        String title,
        String author,
        String uri,
        long durationMs
) {}
