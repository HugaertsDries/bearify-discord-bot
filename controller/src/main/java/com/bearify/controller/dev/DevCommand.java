package com.bearify.controller.dev;

import com.bearify.controller.music.discord.AnnouncerProperties;
import com.bearify.controller.music.discord.PlaybackComponent;
import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.spring.annotation.DiscordController;
import com.bearify.discord.spring.annotation.Interaction;
import com.bearify.discord.spring.annotation.InteractionGroup;
import com.bearify.discord.spring.annotation.Option;
import org.springframework.context.annotation.Profile;

@Profile("dev")
@DiscordController
@InteractionGroup("dev")
public class DevCommand {
    private final DiscordClient discord;
    private final PlaybackComponent playbackComponent;
    private final PlaybackAnnouncerPresets playbackAnnouncerPresets;

    public DevCommand(DiscordClient discord, AnnouncerProperties properties) {
        this.discord = discord;
        this.playbackComponent = new PlaybackComponent(properties);
        this.playbackAnnouncerPresets = new PlaybackAnnouncerPresets(properties.footer());
    }

    @SuppressWarnings("unused")
    @Interaction(value = "playback-preview", description = "Playback Components preview")
    public void playbackPreview(CommandInteraction interaction,
                                @Option(name = "preset", description = "Named preview preset", defaultValue = "broadcast") String preset) {
        PlaybackAnnouncerPreset selectedPreset = PlaybackAnnouncerPreset.from(preset);
        interaction.getTextChannelId().ifPresentOrElse(textChannelId -> {
            discord.textChannel(textChannelId).send(playbackComponent.render(playbackAnnouncerPresets.get(selectedPreset)));
            interaction.reply("Posted playback preview preset `" + selectedPreset.optionValue() + "` in <#" + textChannelId + ">.").ephemeral().send();
        }, () -> interaction.reply("No text channel found for this interaction.").ephemeral().send());
    }
}
