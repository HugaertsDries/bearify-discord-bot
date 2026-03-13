package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.ReplyBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

/**
 * Wraps a JDA {@link SlashCommandInteractionEvent} as a {@link CommandInteraction}.
 */
class JdaCommandInteraction implements CommandInteraction {

    private final SlashCommandInteractionEvent event;

    JdaCommandInteraction(SlashCommandInteractionEvent event) {
        this.event = event;
    }

    @Override
    public String getName() {
        return event.getName();
    }

    @Override
    public ReplyBuilder reply(String message) {
        return new JdaReplyBuilder(event, message);
    }
}
