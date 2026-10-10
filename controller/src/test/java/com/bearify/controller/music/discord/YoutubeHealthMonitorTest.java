package com.bearify.controller.music.discord;

import com.bearify.controller.music.domain.HealthChecker;
import com.bearify.discord.testing.MockDiscordClient;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.TrackMetadata;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class YoutubeHealthMonitorTest {

    private static final String CHANNEL_ID = "status-channel";
    private static final String PLAYER_ID = "player-1";
    private static final String REQUEST_ID = "req-1";
    private static final String GUILD_ID = "guild-1";
    private static final Duration INITIAL_DELAY = Duration.ofMinutes(2);
    private static final Duration INTERVAL = Duration.ofHours(6);
    private static final Duration BROKEN_INTERVAL = Duration.ofMinutes(30);
    private static final Duration PASSIVE_COOLDOWN = Duration.ofMinutes(10);

    private static final String BROKEN = "🔴 | YouTube playback looks broken: ";
    private static final String NO_BEAR = "🔴 | No bear answered the YouTube health check. Is an agent running?";
    private static final String RECOVERED = "🟢 | YouTube playback recovered.";
    private static final String BEAR_BACK = "🟢 | A bear answered the health check again.";

    private final StubClock clock = new StubClock();
    private final StubHealthChecker checker = new StubHealthChecker();
    private final QueueingExecutor executor = new QueueingExecutor();
    private final MockDiscordClient discord = new MockDiscordClient.Factory().create(List.of(), interaction -> {}, Optional.empty());
    private final YoutubeHealthMonitor monitor = monitor(CHANNEL_ID);

    // --- SCHEDULED CHECKS ---

    @Test
    void postsNothingWhenChecksPass() {
        checker.answers(ok());

        tickAfter(INITIAL_DELAY);

        assertThat(checker.calls).isEqualTo(1);
        assertThat(discord.sentMessages(CHANNEL_ID)).isEmpty();
    }

    @Test
    void runsFirstCheckAfterInitialDelay() {
        tickAfter(INITIAL_DELAY.minusMinutes(1));
        assertThat(checker.calls).isZero();

        tickAfter(Duration.ofMinutes(1));
        assertThat(checker.calls).isEqualTo(1);
    }

    @Test
    void runsLaterChecksEveryInterval() {
        tickAfter(INITIAL_DELAY);

        tickAfter(INTERVAL.minusMinutes(1));
        assertThat(checker.calls).isEqualTo(1);

        tickAfter(Duration.ofMinutes(1));
        assertThat(checker.calls).isEqualTo(2);
    }

    @Test
    void checksEveryBrokenIntervalWhileUnhealthy() {
        checker.answers(failed("broken"), Optional.empty(), Optional.empty());

        tickAfter(INITIAL_DELAY);
        tickAfter(BROKEN_INTERVAL.minusMinutes(1));
        assertThat(checker.calls).isEqualTo(1);

        tickAfter(Duration.ofMinutes(1));
        tickAfter(BROKEN_INTERVAL);
        assertThat(checker.calls).as("no bear answering also counts as unhealthy").isEqualTo(3);
    }

    @Test
    void runsScheduledChecksOnTheExecutor() {
        clock.advance(INITIAL_DELAY);

        monitor.tick();

        assertThat(checker.calls).isZero();
        executor.runAll();
        assertThat(checker.calls).isEqualTo(1);
    }

    // --- STATE CHANGES ---

    @Test
    void postsBrokenOnceWhileItStaysBroken() {
        checker.answers(failed("Must find action functions from script"), failed("another reason"));

        tickAfter(INITIAL_DELAY);
        tickAfter(BROKEN_INTERVAL);

        assertThat(checker.calls).isEqualTo(2);
        assertThat(discord.sentMessages(CHANNEL_ID)).containsExactly(BROKEN + "Must find action functions from script");
    }

    @Test
    void postsNoBearAnsweredOnceWhenChecksTimeOut() {
        checker.answers(Optional.empty(), Optional.empty());

        tickAfter(INITIAL_DELAY);
        tickAfter(BROKEN_INTERVAL);

        assertThat(discord.sentMessages(CHANNEL_ID)).containsExactly(NO_BEAR);
    }

    @Test
    void postsRecoveredWhenBrokenTurnsOk() {
        checker.answers(failed("broken"), ok(), ok());

        tickAfter(INITIAL_DELAY);
        tickAfter(BROKEN_INTERVAL);
        tickAfter(INTERVAL);

        assertThat(discord.sentMessages(CHANNEL_ID)).containsExactly(BROKEN + "broken", RECOVERED);
    }

    @Test
    void postsBearBackWhenNoBearTurnsOk() {
        checker.answers(Optional.empty(), ok());

        tickAfter(INITIAL_DELAY);
        tickAfter(BROKEN_INTERVAL);

        assertThat(discord.sentMessages(CHANNEL_ID)).containsExactly(NO_BEAR, BEAR_BACK);
    }

    @Test
    void postsBrokenWhenNoBearTurnsBroken() {
        checker.answers(Optional.empty(), failed("broken"));

        tickAfter(INITIAL_DELAY);
        tickAfter(BROKEN_INTERVAL);

        assertThat(discord.sentMessages(CHANNEL_ID)).containsExactly(NO_BEAR, BROKEN + "broken");
    }

    @Test
    void postsNoBearWhenBrokenTurnsNoBear() {
        checker.answers(failed("broken"), Optional.empty());

        tickAfter(INITIAL_DELAY);
        tickAfter(BROKEN_INTERVAL);

        assertThat(discord.sentMessages(CHANNEL_ID)).containsExactly(BROKEN + "broken", NO_BEAR);
    }

    @Test
    void truncatesLongFailureReasons() {
        checker.answers(failed("x".repeat(500)));

        tickAfter(INITIAL_DELAY);

        assertThat(discord.sentMessages(CHANNEL_ID)).containsExactly(BROKEN + "x".repeat(300));
    }

    // --- PASSIVE TRIGGERS ---

    @Test
    void triggersCheckOnTrackLoadFailed() {
        monitor.accept(loadFailed());
        executor.runAll();

        assertThat(checker.calls).isEqualTo(1);
        assertThat(discord.sentMessages(CHANNEL_ID)).isEmpty();
    }

    @Test
    void triggersCheckOnTrackError() {
        checker.answers(failed("broken"));

        monitor.accept(new MusicPlayerEvent.TrackError(PLAYER_ID, REQUEST_ID, GUILD_ID, track()));
        executor.runAll();

        assertThat(discord.sentMessages(CHANNEL_ID)).containsExactly(BROKEN + "broken");
    }

    @Test
    void neverRunsPassiveCheckOnTheCallingThread() {
        monitor.accept(loadFailed());

        assertThat(checker.calls).as("the Redis listener thread delivers the result, so it must not wait for it").isZero();
        assertThat(executor.queued()).isEqualTo(1);
    }

    @Test
    void ignoresPassiveFailuresWithinCooldownOfLastCheck() {
        tickAfter(INITIAL_DELAY);

        clock.advance(PASSIVE_COOLDOWN.minusMinutes(1));
        monitor.accept(loadFailed());
        executor.runAll();
        assertThat(checker.calls).isEqualTo(1);

        clock.advance(Duration.ofMinutes(1));
        monitor.accept(loadFailed());
        executor.runAll();
        assertThat(checker.calls).isEqualTo(2);
    }

    @Test
    void skipsPassiveCheckWithoutUsingCooldownWhenCheckIsAlreadyRunning() {
        clock.advance(INITIAL_DELAY);
        checker.duringNextCheck = () -> {
            clock.advance(PASSIVE_COOLDOWN.plusMinutes(1));
            monitor.accept(loadFailed());
            executor.runAll();
        };

        monitor.tick();
        executor.runAll();
        assertThat(checker.calls).as("the passive check found a check already running").isEqualTo(1);

        monitor.accept(loadFailed());
        executor.runAll();
        assertThat(checker.calls).isEqualTo(2);
    }

    @Test
    void ignoresOtherEvents() {
        monitor.accept(new MusicPlayerEvent.TrackNotFound(PLAYER_ID, REQUEST_ID, GUILD_ID, "query"));
        monitor.accept(new MusicPlayerEvent.Stopped(PLAYER_ID, REQUEST_ID, GUILD_ID));

        assertThat(executor.queued()).isZero();
    }

    // --- DISABLED ---

    @Test
    void doesNothingWhenChannelIsNotConfigured() {
        YoutubeHealthMonitor disabled = monitor(" ");

        clock.advance(INTERVAL);
        disabled.tick();
        disabled.accept(loadFailed());

        assertThat(executor.queued()).isZero();
    }

    // --- HELPERS ---

    private void tickAfter(Duration elapsed) {
        clock.advance(elapsed);
        monitor.tick();
        executor.runAll();
    }

    private YoutubeHealthMonitor monitor(String channelId) {
        HealthProperties properties = new HealthProperties(
                channelId, INITIAL_DELAY, INTERVAL, BROKEN_INTERVAL, Duration.ofSeconds(30), PASSIVE_COOLDOWN);
        return new YoutubeHealthMonitor(checker, discord, properties, clock, executor);
    }

    private static MusicPlayerEvent.TrackLoadFailed loadFailed() {
        return new MusicPlayerEvent.TrackLoadFailed(PLAYER_ID, REQUEST_ID, GUILD_ID, "This video is private");
    }

    private static Optional<MusicPlayerEvent.HealthCheckResult> ok() {
        return Optional.of(new MusicPlayerEvent.HealthCheckResult(PLAYER_ID, REQUEST_ID, true, Optional.empty()));
    }

    private static Optional<MusicPlayerEvent.HealthCheckResult> failed(String reason) {
        return Optional.of(new MusicPlayerEvent.HealthCheckResult(PLAYER_ID, REQUEST_ID, false, Optional.of(reason)));
    }

    private static TrackMetadata track() {
        return new TrackMetadata("title", "author", "https://example.com/track", 180_000L);
    }

    private static final class StubHealthChecker implements HealthChecker {
        private final Queue<Optional<MusicPlayerEvent.HealthCheckResult>> answers = new ArrayDeque<>();
        private Runnable duringNextCheck = () -> {};
        private int calls;

        @SafeVarargs
        final void answers(Optional<MusicPlayerEvent.HealthCheckResult>... results) {
            answers.addAll(List.of(results));
        }

        @Override
        public Optional<MusicPlayerEvent.HealthCheckResult> check() {
            calls++;
            Runnable during = duringNextCheck;
            duringNextCheck = () -> {};
            during.run();
            return answers.isEmpty() ? ok() : answers.poll();
        }
    }

    /** Holds submitted tasks until the test runs them, so nothing runs on the submitting thread. */
    private static final class QueueingExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }

        void runAll() {
            while (!tasks.isEmpty()) {
                tasks.poll().run();
            }
        }

        int queued() {
            return tasks.size();
        }
    }

    private static final class StubClock extends Clock {
        private Instant now = Instant.parse("2026-10-11T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
