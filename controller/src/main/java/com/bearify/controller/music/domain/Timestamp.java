package com.bearify.controller.music.domain;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A moment in a track, as people type it: {@code 83} (seconds), {@code 1:23}, {@code 1:02:03}, {@code 1h}, {@code 1h30m} or {@code 2m15s}. */
public record Timestamp(Duration position) {

    private static final Pattern SECONDS = Pattern.compile("\\d{1,6}");
    private static final Pattern MINUTES = Pattern.compile("(\\d{1,3}):([0-5]\\d)");
    private static final Pattern HOURS = Pattern.compile("(\\d{1,3}):([0-5]\\d):([0-5]\\d)");
    private static final Pattern UNITS = Pattern.compile("(?:(\\d{1,3})h)?\\s*(?:(\\d{1,4})m)?\\s*(?:(\\d{1,6})s)?");

    public static Optional<Timestamp> parse(String text) {
        String trimmed = text.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        if (SECONDS.matcher(trimmed).matches()) {
            return Optional.of(of(0, 0, Long.parseLong(trimmed)));
        }
        Matcher minutes = MINUTES.matcher(trimmed);
        if (minutes.matches()) {
            return Optional.of(of(0, number(minutes, 1), number(minutes, 2)));
        }
        Matcher hours = HOURS.matcher(trimmed);
        if (hours.matches()) {
            return Optional.of(of(number(hours, 1), number(hours, 2), number(hours, 3)));
        }
        Matcher units = UNITS.matcher(trimmed);
        if (units.matches()) {
            return Optional.of(of(number(units, 1), number(units, 2), number(units, 3)));
        }
        return Optional.empty();
    }

    private static Timestamp of(long hours, long minutes, long seconds) {
        return new Timestamp(Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds));
    }

    // an absent unit, such as the minutes in 1h, counts as 0
    private static long number(Matcher matcher, int group) {
        String value = matcher.group(group);
        return value == null ? 0 : Long.parseLong(value);
    }
}
