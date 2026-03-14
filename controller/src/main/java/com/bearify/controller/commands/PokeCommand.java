package com.bearify.controller.commands;

import com.bearify.discord.api.format.CodeBlockBuilder;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.EditableMessage;
import com.bearify.discord.spring.annotation.Command;
import com.bearify.discord.spring.annotation.Interaction;
import com.bearify.discord.spring.annotation.Option;

import java.time.Duration;
import java.time.Instant;
import java.util.LongSummaryStatistics;

@Command
public class PokeCommand {

    private static final long THRESHOLD_GREEN  = 300;
    private static final long THRESHOLD_YELLOW = 600;

    @SuppressWarnings("unused")
    @Interaction(value = "poke", description = "Poke the bear. Dare you.")
    public void poke(CommandInteraction interaction,
                     @Option(name = "pokes", description = "Number of times to poke the bear", defaultValue = "4") int pokes) {
        EditableMessage handle = interaction.defer();

        LongSummaryStatistics stats = new LongSummaryStatistics();
        CodeBlockBuilder cb = new CodeBlockBuilder().append("🐻 POKING bearify...").newline();
        handle.edit(cb.toString());

        for (int i = 0; i < pokes; i++) {
            Instant start = Instant.now();
            handle.edit(cb.toString()); // round-trip edit to measure latency
            long ms = Duration.between(start, Instant.now()).toMillis();
            stats.accept(ms);

            cb.newline().append(String.format("🐾  #%2d  %6dms  %s", i + 1, ms, indicator(ms)));
            handle.edit(cb.toString());
        }

        long avg = (long) stats.getAverage();
        cb.blank();
        cb.append(String.format("🐻 %d pokes survived. %s", pokes, verdict(avg))).newline();
        cb.append(String.format("🍯 min=%dms  avg=%dms  max=%dms  %s", stats.getMin(), avg, stats.getMax(), indicator(avg))).newline();
        handle.edit(cb.toString());
    }

    private String indicator(long ms) {
        if (ms < THRESHOLD_GREEN)  return "🟢";
        if (ms < THRESHOLD_YELLOW) return "🟡";
        return "🔴";
    }

    private String verdict(long avg) {
        if (avg < THRESHOLD_GREEN)  return "no sweat.";
        if (avg < THRESHOLD_YELLOW) return "barely.";
        return "send help.";
    }
}
