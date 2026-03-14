package com.bearify.controller.commands;

import com.bearify.discord.testing.MockCommandInteraction;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PokeCommandTest {

    private static final long LOW_LATENCY      = 100; // < 300ms threshold
    private static final long MODERATE_LATENCY = 450; // 300–599ms threshold
    private static final long HIGH_LATENCY     = 700; // >= 600ms threshold

    private static final String GREEN  = "🟢";
    private static final String YELLOW = "🟡";
    private static final String RED    = "🔴";

    private static final String VERDICT_GREEN  = "no sweat.";
    private static final String VERDICT_YELLOW = "barely.";
    private static final String VERDICT_RED    = "send help.";

    static class StubClock extends Clock {
        private final long latencyMs;
        private int calls = 0;

        StubClock(long latencyMs) { this.latencyMs = latencyMs; }

        @Override
        public Instant instant() {
            return calls++ % 2 == 0 ? Instant.EPOCH : Instant.EPOCH.plusMillis(latencyMs);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    // --- HAPPY PATH ---

    @Test
    void pokes() {
        PokeCommand command = new PokeCommand(new StubClock(LOW_LATENCY));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("poke").build();

        command.poke(interaction, 4);

        String output = interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow();
        assertThat(output).contains("POKING bearify");
        assertThat(output).contains("# 1", "# 2", "# 3", "# 4");
        assertThat(output).doesNotContain("# 5");
        assertThat(output).contains("pokes survived");
    }

    @Test
    void pokesWithCustomCount() {
        PokeCommand command = new PokeCommand(new StubClock(LOW_LATENCY));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("poke").build();

        command.poke(interaction, 2);

        String output = interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow();
        assertThat(output).contains("# 1", "# 2");
        assertThat(output).doesNotContain("# 3");
    }

    // --- CAPABILITIES ---

    @Test
    void showsGreenIndicatorWhenLatencyIsLow() {
        PokeCommand command = new PokeCommand(new StubClock(LOW_LATENCY));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("poke").build();

        command.poke(interaction, 1);

        String output = interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow();
        assertThat(output).contains(GREEN);
        assertThat(output).doesNotContain(YELLOW, RED);
        assertThat(output).contains(LOW_LATENCY + "ms");
    }

    @Test
    void showsYellowIndicatorWhenLatencyIsModerate() {
        PokeCommand command = new PokeCommand(new StubClock(MODERATE_LATENCY));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("poke").build();

        command.poke(interaction, 1);

        String output = interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow();
        assertThat(output).contains(YELLOW);
        assertThat(output).doesNotContain(GREEN, RED);
        assertThat(output).contains(MODERATE_LATENCY + "ms");
    }

    @Test
    void showsRedIndicatorWhenLatencyIsHigh() {
        PokeCommand command = new PokeCommand(new StubClock(HIGH_LATENCY));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("poke").build();

        command.poke(interaction, 1);

        String output = interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow();
        assertThat(output).contains(RED);
        assertThat(output).doesNotContain(GREEN, YELLOW);
        assertThat(output).contains(HIGH_LATENCY + "ms");
    }

    @Test
    void showsNoSweatVerdictWhenLatencyIsLow() {
        PokeCommand command = new PokeCommand(new StubClock(LOW_LATENCY));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("poke").build();

        command.poke(interaction, 1);

        String output = interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow();
        assertThat(output).contains(VERDICT_GREEN);
        assertThat(output).contains(
                "min=" + LOW_LATENCY + "ms",
                "avg=" + LOW_LATENCY + "ms",
                "max=" + LOW_LATENCY + "ms"
        );
    }

    @Test
    void showsBarelyVerdictWhenLatencyIsModerate() {
        PokeCommand command = new PokeCommand(new StubClock(MODERATE_LATENCY));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("poke").build();

        command.poke(interaction, 1);

        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains(VERDICT_YELLOW);
    }

    @Test
    void showsSendHelpVerdictWhenLatencyIsHigh() {
        PokeCommand command = new PokeCommand(new StubClock(HIGH_LATENCY));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("poke").build();

        command.poke(interaction, 1);

        assertThat(interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow())
                .contains(VERDICT_RED);
    }

    @Test
    void repliesArePrivate() {
        PokeCommand command = new PokeCommand(new StubClock(LOW_LATENCY));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("poke").build();

        command.poke(interaction, 1);

        assertThat(interaction.isDeferredEphemeral()).isTrue();
    }

    // --- EDGE CASES ---

    @Test
    void zeroPokeStillSendsTitleAndReport() {
        PokeCommand command = new PokeCommand(new StubClock(LOW_LATENCY));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("poke").build();

        command.poke(interaction, 0);

        String output = interaction.getDeferredMessage().orElseThrow().getLastEdit().orElseThrow();
        assertThat(output).contains("POKING bearify");
        assertThat(output).contains("0 pokes survived");
        assertThat(output).doesNotContain("# 1");
    }
}
