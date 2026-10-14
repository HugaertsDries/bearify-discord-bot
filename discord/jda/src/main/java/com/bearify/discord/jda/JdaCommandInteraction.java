package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.CommandInteraction;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.time.Duration;
import java.util.Optional;

/**
 * Wraps a JDA {@link SlashCommandInteractionEvent} as a {@link CommandInteraction}.
 */
class JdaCommandInteraction extends JdaRepliableInteraction<SlashCommandInteractionEvent> implements CommandInteraction {

    JdaCommandInteraction(SlashCommandInteractionEvent event, Optional<Duration> ephemeralDeleteAfter) {
        super(event, ephemeralDeleteAfter);
    }

    @Override
    public Optional<String> getOption(String name) {
        return Optional.ofNullable(event.getOption(name)).map(OptionMapping::getAsString);
    }

    @Override
    public Optional<String> getSubcommandName() {
        return Optional.ofNullable(event.getSubcommandName());
    }
}
