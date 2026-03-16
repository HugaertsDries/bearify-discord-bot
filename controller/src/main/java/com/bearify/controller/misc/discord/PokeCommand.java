package com.bearify.controller.misc.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.discord.api.format.CodeBlockBuilder;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.EditableMessage;
import com.bearify.discord.spring.annotation.Command;
import com.bearify.discord.spring.annotation.Interaction;
import com.bearify.discord.spring.annotation.Option;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LongSummaryStatistics;

@Command
public class PokeCommand {

    private static final long THRESHOLD_GREEN = 300;
    private static final long THRESHOLD_YELLOW = 600;

    private final Clock clock;

    public PokeCommand(Clock clock) {
        this.clock = clock;
    }

    @SuppressWarnings("unused")
    @Interaction(value = "poke", description = "Poke the bear. Dare you.")
    public void poke(CommandInteraction interaction,
                     @Option(name = "pokes", description = "Number of times to poke the bear", defaultValue = "4") int pokes) {
        EditableMessage handle = interaction.defer();

        LongSummaryStatistics stats = new LongSummaryStatistics();
        CodeBlockBuilder cb = new CodeBlockBuilder().append(BearifyEmoji.BEAR + " POKING bearify...").newline();
        handle.edit(cb.toString());

        for (int i = 0; i < pokes; i++) {
            Instant start = clock.instant();
            handle.edit(cb.toString());
            long ms = Duration.between(start, clock.instant()).toMillis();
            stats.accept(ms);

            cb.newline().append(String.format("%s  #%2d  %6dms  %s", BearifyEmoji.PAW, i + 1, ms, indicator(ms)));
            handle.edit(cb.toString());
        }

        long min = stats.getCount() == 0 ? 0 : stats.getMin();
        long avg = stats.getCount() == 0 ? 0 : (long) stats.getAverage();
        long max = stats.getCount() == 0 ? 0 : stats.getMax();
        cb.blank();
        cb.append(String.format("%s %d pokes survived. %s", BearifyEmoji.BEAR, pokes, verdict(avg))).newline();
        cb.append(String.format("%s min=%dms  avg=%dms  max=%dms  %s", BearifyEmoji.HONEY, min, avg, max, indicator(avg))).newline();
        handle.edit(cb.toString());
    }

    private String indicator(long ms) {
        if (ms < THRESHOLD_GREEN) {
            return BearifyEmoji.GREEN;
        }
        if (ms < THRESHOLD_YELLOW) {
            return BearifyEmoji.YELLOW;
        }
        return BearifyEmoji.RED;
    }

    private String verdict(long avg) {
        if (avg < THRESHOLD_GREEN) {
            return "no sweat.";
        }
        if (avg < THRESHOLD_YELLOW) {
            return "barely.";
        }
        return "send help.";
    }
}
