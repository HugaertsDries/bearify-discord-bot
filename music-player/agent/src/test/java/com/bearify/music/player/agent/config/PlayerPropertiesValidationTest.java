package com.bearify.music.player.agent.config;

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
        var voiceSession = new PlayerProperties.VoiceSession(Duration.ZERO, Duration.ofMinutes(5));
        var violations = validator.validate(voiceSession);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void rejectsVoiceSessionLonelyTimeoutOfZero() {
        var voiceSession = new PlayerProperties.VoiceSession(Duration.ofSeconds(10), Duration.ZERO);
        var violations = validator.validate(voiceSession);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void acceptsPositiveVoiceSessionHeartbeatConfiguration() {
        var voiceSession = new PlayerProperties.VoiceSession(Duration.ofSeconds(10), Duration.ofMinutes(5));
        var violations = validator.validate(voiceSession);
        assertThat(violations).isEmpty();
    }
}
