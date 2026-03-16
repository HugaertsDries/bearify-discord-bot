package com.bearify.controller.misc.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.discord.testing.MockCommandInteraction;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PokeCommandTest {

    private static final long LOW_LATENCY = 100;
    private static final long MODERATE_LATENCY = 450;
    private static final long HIGH_LATENCY = 700;

    static class StubClock extends Clock {
        private final long latencyMs;
        private int calls = 0;

        StubClock(long latencyMs) {
            this.latencyMs = latencyMs;
        }

        @Override
        public Instant instant() {
            return calls++ % 2 == 0 ? Instant.EPOCH : Instant.EPOCH.plusMillis(latencyMs);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    // --- HAPPY PATH ---

    @Test
    void pokesConfiguredNumberOfTimes() {
        PokeCommand command = new PokeCommand(new StubClock(LOW_LATENCY));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("poke").build();

        command.poke(interaction, 4);

        String output = interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow();
        assertThat(output).contains("POKING bearify");
        assertThat(output).contains("# 1", "# 2", "# 3", "# 4");
        assertThat(output).doesNotContain("# 5");
        assertThat(output).contains("4 pokes survived");
    }

    // --- LATENCY BANDS ---

    @Test
    void showsGreenIndicatorForLowLatency() {
        PokeCommand command = new PokeCommand(new StubClock(LOW_LATENCY));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("poke").build();

        command.poke(interaction, 1);

        String output = interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow();
        assertThat(output).contains(BearifyEmoji.GREEN);
        assertThat(output).doesNotContain(BearifyEmoji.YELLOW, BearifyEmoji.RED);
        assertThat(output).contains("no sweat.");
    }

    @Test
    void showsYellowIndicatorForModerateLatency() {
        PokeCommand command = new PokeCommand(new StubClock(MODERATE_LATENCY));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("poke").build();

        command.poke(interaction, 1);

        String output = interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow();
        assertThat(output).contains(BearifyEmoji.YELLOW);
        assertThat(output).doesNotContain(BearifyEmoji.GREEN, BearifyEmoji.RED);
        assertThat(output).contains("barely.");
    }

    @Test
    void showsRedIndicatorForHighLatency() {
        PokeCommand command = new PokeCommand(new StubClock(HIGH_LATENCY));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("poke").build();

        command.poke(interaction, 1);

        String output = interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow();
        assertThat(output).contains(BearifyEmoji.RED);
        assertThat(output).doesNotContain(BearifyEmoji.GREEN, BearifyEmoji.YELLOW);
        assertThat(output).contains("send help.");
    }

    // --- EDGE CASES ---

    @Test
    void zeroPokesReportsZeroLatencySummary() {
        PokeCommand command = new PokeCommand(new StubClock(LOW_LATENCY));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("poke").build();

        command.poke(interaction, 0);

        String output = interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow();
        assertThat(output).contains("0 pokes survived");
        assertThat(output).contains("min=0ms", "avg=0ms", "max=0ms");
    }
}
