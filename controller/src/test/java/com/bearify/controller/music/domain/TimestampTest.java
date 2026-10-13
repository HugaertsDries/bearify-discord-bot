package com.bearify.controller.music.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TimestampTest {

    // happy path

    @Test
    void parsesPlainSeconds() {
        assertThat(Timestamp.parse("83")).contains(new Timestamp(Duration.ofSeconds(83)));
    }

    @Test
    void parsesMinutesAndSeconds() {
        assertThat(Timestamp.parse("1:23")).contains(new Timestamp(Duration.ofSeconds(83)));
    }

    @Test
    void parsesHoursMinutesAndSeconds() {
        assertThat(Timestamp.parse("1:02:03")).contains(new Timestamp(Duration.ofSeconds(3723)));
    }

    @Test
    void parsesUnits() {
        assertThat(Timestamp.parse("1h")).contains(new Timestamp(Duration.ofHours(1)));
        assertThat(Timestamp.parse("1h30m")).contains(new Timestamp(Duration.ofMinutes(90)));
        assertThat(Timestamp.parse("2m15s")).contains(new Timestamp(Duration.ofSeconds(135)));
        assertThat(Timestamp.parse("90s")).contains(new Timestamp(Duration.ofSeconds(90)));
        assertThat(Timestamp.parse("1h 2m 3s")).contains(new Timestamp(Duration.ofSeconds(3723)));
    }

    // exceptions

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "-3", "1:5", "1:75", "1:2:3", "1:60:00", "1:23:", "1234567", "h", "1x", "1s2m", "1h:30"})
    void rejectsMalformedTimes(String text) {
        assertThat(Timestamp.parse(text)).isEmpty();
    }

    // edge cases

    @Test
    void ignoresSurroundingSpacesAndCase() {
        assertThat(Timestamp.parse(" 1:23 ")).contains(new Timestamp(Duration.ofSeconds(83)));
        assertThat(Timestamp.parse("1H")).contains(new Timestamp(Duration.ofHours(1)));
    }
}
