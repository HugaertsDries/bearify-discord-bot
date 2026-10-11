package com.bearify.music.player.agent.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerPropertiesValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsAssignmentTtlSmallerThanHeartbeatInterval() {
        var assignment = new PlayerProperties.Assignment(Duration.ofSeconds(5), Duration.ofSeconds(10));
        var violations = validator.validate(assignment);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void acceptsAssignmentTtlGreaterThanHeartbeatInterval() {
        var assignment = new PlayerProperties.Assignment(Duration.ofSeconds(30), Duration.ofSeconds(10));
        var violations = validator.validate(assignment);
        assertThat(violations).isEmpty();
    }

    @Test
    void rejectsVoiceSessionHeartbeatIntervalOfZero() {
        var voiceSession = new PlayerProperties.VoiceSession(Duration.ZERO, Duration.ofMinutes(5), Duration.ofMinutes(10));
        var violations = validator.validate(voiceSession);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void rejectsNonPositiveNobodyListeningTimeout() {
        var voiceSession = new PlayerProperties.VoiceSession(Duration.ofSeconds(10), Duration.ZERO, Duration.ofMinutes(10));
        var violations = validator.validate(voiceSession);
        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactly("music-player.voice-session.nobody-listening-timeout must be greater than 0");
    }

    @Test
    void rejectsNonPositiveIdleTimeout() {
        var voiceSession = new PlayerProperties.VoiceSession(Duration.ofSeconds(10), Duration.ofMinutes(5), Duration.ofMinutes(-1));
        var violations = validator.validate(voiceSession);
        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .containsExactly("music-player.voice-session.idle-timeout must be greater than 0");
    }

    @Test
    void acceptsPositiveVoiceSessionHeartbeatConfiguration() {
        var voiceSession = new PlayerProperties.VoiceSession(Duration.ofSeconds(10), Duration.ofMinutes(5), Duration.ofMinutes(10));
        var violations = validator.validate(voiceSession);
        assertThat(violations).isEmpty();
    }

    @Test
    void treatsBlankRefreshTokenAsAbsent() {
        var youtube = new PlayerProperties.Engine.Youtube(" ", null, null);
        assertThat(youtube.refreshToken()).isEmpty();
    }
}
