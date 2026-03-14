package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.EditableMessage;
import com.bearify.discord.api.interaction.ReplyBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.time.Instant;
import java.util.Optional;

/**
 * Wraps a JDA {@link SlashCommandInteractionEvent} as a {@link CommandInteraction}.
 */
class JdaCommandInteraction implements CommandInteraction {

    private final SlashCommandInteractionEvent event;

    JdaCommandInteraction(SlashCommandInteractionEvent event) {
        this.event = event;
    }

    @Override
    public EditableMessage defer() {
        Instant createdAt = Instant.now();
        InteractionHook hook = event.deferReply(true).complete();
        return new JdaEditableMessage(hook, createdAt);
    }

    @Override
    public ReplyBuilder reply(String message) {
        return new JdaReplyBuilder(event, message);
    }

    @Override
    public String getName() {
        return event.getName();
    }

    @Override
    public Optional<String> getOption(String name) {
        return Optional.ofNullable(event.getOption(name)).map(OptionMapping::getAsString);
    }
}
