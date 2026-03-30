package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.ButtonInteraction;
import com.bearify.discord.api.interaction.ReplyBuilder;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import java.util.Optional;

class JdaButtonInteraction implements ButtonInteraction {

    private final ButtonInteractionEvent event;

    JdaButtonInteraction(ButtonInteractionEvent event) {
        this.event = event;
    }

    @Override
    public String getCustomId() {
        return event.getComponentId();
    }

    @Override
    public ReplyBuilder reply(String message) {
        return new JdaReplyBuilder(event, message);
    }

    @Override
    public void acknowledge() {
        event.deferEdit().queue();
    }

    @Override
    public Optional<String> getGuildId() {
        return Optional.ofNullable(event.getGuild()).map(net.dv8tion.jda.api.entities.Guild::getId);
    }

    @Override
    public Optional<String> getTextChannelId() {
        return Optional.ofNullable(event.getChannelId());
    }

    @Override
    public Optional<String> getVoiceChannelId() {
        return Optional.ofNullable(event.getMember())
                .map(Member::getVoiceState)
                .filter(GuildVoiceState::inAudioChannel)
                .flatMap(vs -> Optional.ofNullable(vs.getChannel()).map(ISnowflake::getId));
    }

    @Override
    public String getUserMention() {
        return Optional.ofNullable(event.getMember())
                .map(Member::getAsMention)
                .orElseGet(() -> event.getUser().getAsMention());
    }
}
