package com.bearify.controller.music.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.controller.music.domain.HealthChecker;
import com.bearify.controller.music.domain.MusicPlayerEventConsumer;
import com.bearify.discord.api.format.TextCut;
import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Posts to the owner's status channel only when the health state changes (working, YouTube broken,
 * no bear answering), so an outage gives one alert and one recovery message, not one per check.
 * Real playback failures only trigger a probe of a known-good track, so a dead link a friend
 * pastes never raises an alarm.
 */
public class YoutubeHealthMonitor implements MusicPlayerEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(YoutubeHealthMonitor.class);
    private static final int MAX_REASON_LENGTH = 300;

    static final String BROKEN_MESSAGE = BearifyEmoji.RED + " | YouTube playback looks broken: ";
    static final String NO_BEAR_MESSAGE = BearifyEmoji.RED + " | No bear answered the YouTube health check. Is an agent running?";
    static final String RECOVERED_MESSAGE = BearifyEmoji.GREEN + " | YouTube playback recovered.";
    static final String BEAR_BACK_MESSAGE = BearifyEmoji.GREEN + " | A bear answered the health check again.";

    private enum State { OK, NO_BEAR, BROKEN }

    private enum Trigger { SCHEDULED, PASSIVE }

    private final HealthChecker checker;
    private final DiscordClient discord;
    private final HealthProperties properties;
    private final Clock clock;
    private final Executor executor;
    private final AtomicBoolean checking = new AtomicBoolean();
    private final Instant startedAt;

    private volatile State state = State.OK;
    private volatile Optional<String> brokenReason = Optional.empty();
    private volatile Optional<Instant> lastCheckStart = Optional.empty();

    public YoutubeHealthMonitor(HealthChecker checker,
                                DiscordClient discord,
                                HealthProperties properties,
                                Clock clock,
                                Executor executor) {
        this.checker = checker;
        this.discord = discord;
        this.properties = properties;
        this.clock = clock;
        this.executor = executor;
        this.startedAt = clock.instant();
    }

    @Override
    public void accept(MusicPlayerEvent event) {
        if (properties.channelId().isEmpty()) {
            return;
        }
        if (event instanceof MusicPlayerEvent.TrackLoadFailed || event instanceof MusicPlayerEvent.TrackError) {
            // never on the Redis listener thread: it delivers the result this check waits for
            executor.execute(() -> check(Trigger.PASSIVE));
        }
    }

    // ticks often and lets the interval properties decide whether a check is due
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void tick() {
        if (properties.channelId().isEmpty()) {
            return;
        }
        // keeps Spring's single scheduler thread free while a check waits up to bearify.health.timeout
        executor.execute(() -> check(Trigger.SCHEDULED));
    }

    private void check(Trigger trigger) {
        if (!checking.compareAndSet(false, true)) {
            return;
        }
        try {
            Instant now = clock.instant();
            if (!isDue(trigger, now)) {
                return;
            }
            lastCheckStart = Optional.of(now);
            Optional<MusicPlayerEvent.HealthCheckResult> result = checker.check();
            if (result.isEmpty()) {
                moveTo(State.NO_BEAR, Optional.empty());
            } else if (result.get().ok()) {
                moveTo(State.OK, Optional.empty());
            } else {
                moveTo(State.BROKEN, Optional.of(TextCut.cut(result.get().reason().orElse("unknown error"), MAX_REASON_LENGTH)));
            }
        } catch (RuntimeException e) {
            LOG.warn("YouTube health check failed to run", e);
        } finally {
            checking.set(false);
        }
    }

    private boolean isDue(Trigger trigger, Instant now) {
        Instant next = switch (trigger) {
            case PASSIVE -> lastCheckStart.map(last -> last.plus(properties.passiveCooldown())).orElse(Instant.MIN);
            case SCHEDULED -> lastCheckStart
                    .map(last -> last.plus(state == State.OK ? properties.interval() : properties.brokenInterval()))
                    .orElse(startedAt.plus(properties.initialDelay()));
        };
        return !now.isBefore(next);
    }

    private void moveTo(State next, Optional<String> reason) {
        State previous = state;
        Optional<String> previousReason = brokenReason;
        state = next;
        brokenReason = reason;
        if (previous == next) {
            // the channel stays quiet, so the log is the only sign the canary is still checking
            LOG.info("YouTube health check: still {}{}", next, reason.map(r -> " (" + r + ")").orElse(""));
            return;
        }
        LOG.info("YouTube health check moved from {}{} to {}{}",
                previous, previousReason.map(r -> " (" + r + ")").orElse(""), next, reason.map(r -> " (" + r + ")").orElse(""));
        switch (next) {
            case BROKEN -> post(BROKEN_MESSAGE + reason.orElseThrow());
            case NO_BEAR -> post(NO_BEAR_MESSAGE);
            case OK -> post(previous == State.BROKEN ? RECOVERED_MESSAGE : BEAR_BACK_MESSAGE);
        }
    }

    private void post(String message) {
        String channelId = properties.channelId().orElseThrow();
        try {
            discord.textChannel(channelId).send(message);
        } catch (RuntimeException e) {
            LOG.warn("Failed to post the YouTube health alert to channel {}", channelId, e);
        }
    }
}
