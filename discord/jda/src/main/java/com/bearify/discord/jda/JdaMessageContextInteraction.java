package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.MessageContextInteraction;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;

import java.time.Duration;
import java.util.Optional;

/**
 * Wraps a JDA {@link MessageContextInteractionEvent} as a {@link MessageContextInteraction}.
 * Discord sends the target message's content with the interaction, so this needs no message content intent.
 */
class JdaMessageContextInteraction extends JdaRepliableInteraction<MessageContextInteractionEvent> implements MessageContextInteraction {

    JdaMessageContextInteraction(MessageContextInteractionEvent event, Optional<Duration> ephemeralDeleteAfter) {
        super(event, ephemeralDeleteAfter);
    }

    @Override
    public String getTargetMessageContent() {
        return event.getTarget().getContentRaw();
    }

    @Override
    public String getTargetAuthorMention() {
        return event.getTarget().getAuthor().getAsMention();
    }
}
