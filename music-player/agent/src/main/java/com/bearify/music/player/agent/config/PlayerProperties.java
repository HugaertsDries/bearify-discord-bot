package com.bearify.music.player.agent.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Optional;

@ConfigurationProperties("music-player")
public record PlayerProperties(
        @DefaultValue("3s")  Duration previousRestartThreshold,
        @DefaultValue("10s") Duration seekShortDefault,
        @DefaultValue("30s") Duration seekLongDefault,
        @DefaultValue("5m")  Duration seekTrackLengthThreshold,
        @DefaultValue("5s")  Duration errorSkipDelay,
        @DefaultValue("200") @Positive int playlistMaxTracks,
        @DefaultValue Assignment assignment,
        @DefaultValue VoiceSession voiceSession,
        @DefaultValue Engine engine) {

    public record Assignment(
            @DefaultValue("30s") Duration ttl,
            @DefaultValue("10s") Duration heartbeatInterval) {

        @AssertTrue(message = "music-player.assignment.ttl must be greater than music-player.assignment.heartbeat-interval")
        public boolean isTtlGreaterThanHeartbeatInterval() {
            return ttl.compareTo(heartbeatInterval) > 0;
        }
    }

    public record VoiceSession(
            @DefaultValue("10s") Duration heartbeatInterval,
            @DefaultValue("5m") Duration lonelyTimeout) {

        @AssertTrue(message = "music-player.voice-session.heartbeat-interval must be greater than 0")
        public boolean hasPositiveHeartbeatInterval() {
            return heartbeatInterval.compareTo(Duration.ZERO) > 0;
        }

        @AssertTrue(message = "music-player.voice-session.lonely-timeout must be greater than 0")
        public boolean hasPositiveLonelyTimeout() {
            return lonelyTimeout.compareTo(Duration.ZERO) > 0;
        }
    }

    public record Engine(@DefaultValue Youtube youtube) {

        public static final class Youtube {
            private final Optional<String> refreshToken;
            private final Optional<String> remoteCipherUrl;
            private final Optional<String> remoteCipherPassword;

            public Youtube(String refreshToken, String remoteCipherUrl, String remoteCipherPassword) {
                this.refreshToken = Optional.ofNullable(refreshToken);
                this.remoteCipherUrl = Optional.ofNullable(remoteCipherUrl).filter(url -> !url.isBlank());
                this.remoteCipherPassword = Optional.ofNullable(remoteCipherPassword).filter(password -> !password.isBlank());
            }

            public Optional<String> refreshToken() {
                return refreshToken;
            }

            public Optional<String> remoteCipherUrl() {
                return remoteCipherUrl;
            }

            public Optional<String> remoteCipherPassword() {
                return remoteCipherPassword;
            }
        }
    }
}
