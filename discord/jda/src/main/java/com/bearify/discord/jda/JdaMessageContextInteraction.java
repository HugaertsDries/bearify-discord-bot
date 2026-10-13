package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.EditableMessage;
import com.bearify.discord.api.interaction.MessageContextInteraction;
import com.bearify.discord.api.interaction.ReplyBuilder;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Wraps a JDA {@link MessageContextInteractionEvent} as a {@link MessageContextInteraction}.
 * Discord sends the target message's content with the interaction, so this needs no message content intent.
 */
class JdaMessageContextInteraction implements MessageContextInteraction {

    private final MessageContextInteractionEvent event;
    private final Optional<Duration> ephemeralDeleteAfter;
    private volatile Optional<JdaEditableMessage> deferredReply = Optional.empty();

    JdaMessageContextInteraction(MessageContextInteractionEvent event, Optional<Duration> ephemeralDeleteAfter) {
        this.event = event;
        this.ephemeralDeleteAfter = ephemeralDeleteAfter;
    }

    @Override
    public EditableMessage defer(boolean ephemeral) {
        Instant createdAt = Instant.now();
        InteractionHook hook = event.deferReply(ephemeral).complete();
        JdaEditableMessage message = new JdaEditableMessage(hook, createdAt, ephemeral ? ephemeralDeleteAfter : Optional.empty());
        deferredReply = Optional.of(message);
        return message;
    }

    @Override
    public ReplyBuilder reply(String message) {
        return new JdaReplyBuilder(event, message, deferredReply, ephemeralDeleteAfter);
    }

    @Override
    public String getName() {
        return event.getName();
    }

    @Override
    public String getTargetMessageContent() {
        return event.getTarget().getContentRaw();
    }

    @Override
    public String getTargetAuthorMention() {
        return event.getTarget().getAuthor().getAsMention();
    }

    @Override
    public String getUserMention() {
        return Optional.ofNullable(event.getMember())
                .map(Member::getAsMention)
                .orElseGet(() -> event.getUser().getAsMention());
    }

    @Override
    public Optional<String> getVoiceChannelId() {
        return Optional.ofNullable(event.getMember())
                .map(Member::getVoiceState)
                .filter(GuildVoiceState::inAudioChannel)
                .flatMap(vs -> Optional.ofNullable(vs.getChannel()).map(ISnowflake::getId));
    }

    @Override
    public Optional<String> getGuildId() {
        return Optional.ofNullable(event.getGuild()).map(net.dv8tion.jda.api.entities.Guild::getId);
    }

    @Override
    public Optional<String> getTextChannelId() {
        return Optional.ofNullable(event.getChannelId());
    }
}
