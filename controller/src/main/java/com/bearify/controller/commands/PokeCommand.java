package com.bearify.controller.commands;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.EditableMessage;
import com.bearify.discord.spring.annotation.Command;
import com.bearify.discord.spring.annotation.Interaction;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Command
public class PokeCommand {

    private static final int POKES = 4;
    private static final String[] SEQ_FLAVORS = {
            "still awake 👀",
            "definitely awake 🐻",
            "had too much honey 🍯",
            "considering hibernation 💤"
    };

    @Interaction(value = "poke", description = "Poke the bear. Dare you.")
    public void ping(CommandInteraction interaction) {
        EditableMessage handle = interaction.defer();

        List<Long> times = new ArrayList<>();
        StringBuilder sb = new StringBuilder("🐻 **POKING** bearify...\n\n");
        handle.edit(sb.toString());

        for (int i = 0; i < POKES; i++) {
            Instant start = Instant.now();
            handle.edit(sb.toString()); // round-trip edit to measure latency
            long ms = Duration.between(start, Instant.now()).toMillis();
            times.add(ms);

            sb.append(String.format("🐾 seq=%d time=%dms — %s\n", i + 1, ms, SEQ_FLAVORS[i]));
            handle.edit(sb.toString());
        }

        long min = times.stream().mapToLong(Long::longValue).min().orElse(0);
        long max = times.stream().mapToLong(Long::longValue).max().orElse(0);
        long avg = (long) times.stream().mapToLong(Long::longValue).average().orElse(0);

        sb.append(String.format("\n🐻 %d pokes survived. barely. min=%dms avg=%dms max=%dms", POKES, min, avg, max));
        handle.edit(sb.toString());
    }
}
