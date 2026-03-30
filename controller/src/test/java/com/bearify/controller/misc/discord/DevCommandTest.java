package com.bearify.controller.misc.discord;

import com.bearify.controller.dev.DevCommand;
import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.Guild;
import com.bearify.discord.api.gateway.SentMessage;
import com.bearify.discord.api.gateway.TextChannel;
import com.bearify.discord.api.message.ComponentMessage;
import com.bearify.discord.api.message.Container;
import com.bearify.discord.api.message.ContainerChild;
import com.bearify.discord.api.message.Section;
import com.bearify.discord.api.message.TextBlock;
import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.discord.api.voice.VoiceSessionListener;
import com.bearify.discord.testing.MockCommandInteraction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DevCommandTest {

    @Test
    void playbackPreviewPostsNamedPresetThroughSharedAnnouncer() {
        List<ComponentMessage> sent = new ArrayList<>();
        DevCommand command = new DevCommand(discord(sent));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("dev")
                .subcommand("playback-preview")
                .textChannelId("text-1")
                .option("preset", "broadcast")
                .build();

        command.playbackPreview(interaction, "broadcast");

        assertThat(sent).singleElement();
        ComponentMessage broadcast = sent.getFirst();
        assertThat(broadcast.label()).isEqualTo("playback-announcer");
        assertThat(broadcast.containers()).hasSize(4);
        assertThat(broadcast.containers().get(0).accentColor()).isEqualTo(0xFDB529);
        assertThat(allTexts(broadcast))
                .anyMatch(text -> text.contains("## Up Next"))
                .anyMatch(text -> text.contains("Hotel California"));
        assertThat(interaction.getReplies()).singleElement().satisfies(reply -> {
            assertThat(reply.getContent()).contains("preset `broadcast`");
            assertThat(reply.isEphemeral()).isTrue();
            assertThat(reply.isSent()).isTrue();
        });
    }

    @Test
    void playbackPreviewFallsBackToBroadcastPresetWhenPresetIsUnknown() {
        List<ComponentMessage> sent = new ArrayList<>();
        DevCommand command = new DevCommand(discord(sent));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("dev")
                .subcommand("playback-preview")
                .textChannelId("text-1")
                .option("preset", "unknown")
                .build();

        command.playbackPreview(interaction, "unknown");

        assertThat(sent).singleElement();
        assertThat(interaction.getReplies()).singleElement().satisfies(reply -> {
            assertThat(reply.getContent()).contains("preset `broadcast`");
            assertThat(reply.isEphemeral()).isTrue();
            assertThat(reply.isSent()).isTrue();
        });
    }

    @Test
    void playbackPreviewRepliesEphemeralWhenInteractionHasNoTextChannel() {
        DevCommand command = new DevCommand(discord(new ArrayList<>()));
        MockCommandInteraction interaction = MockCommandInteraction.forCommand("dev")
                .subcommand("playback-preview")
                .build();

        command.playbackPreview(interaction, "broadcast");

        assertThat(interaction.getReplies()).singleElement().satisfies(reply -> {
            assertThat(reply.getContent()).contains("No text channel found");
            assertThat(reply.isEphemeral()).isTrue();
            assertThat(reply.isSent()).isTrue();
        });
    }

    private static DiscordClient discord(List<ComponentMessage> sentComponents) {
        return new DiscordClient() {
            @Override
            public void start(String token) {
            }

            @Override
            public void start(String token, String guildId) {
            }

            @Override
            public Guild guild(String guildId) {
                return new Guild() {
                    @Override
                    public Optional<VoiceSession> voice() {
                        return Optional.empty();
                    }

                    @Override
                    public void join(String channelId, AudioProvider provider, VoiceSessionListener onJoined) {
                    }
                };
            }

            @Override
            public TextChannel textChannel(String channelId) {
                return new TextChannel() {
                    @Override
                    public void send(String message) {
                    }

                    @Override
                    public SentMessage send(ComponentMessage message) {
                        sentComponents.add(message);
                        return new SentMessage() {
                            @Override
                            public void delete() {
                            }

                            @Override
                            public void update(ComponentMessage updated) {
                            }
                        };
                    }
                };
            }

            @Override
            public void shutdown() {
            }
        };
    }

    private static List<String> allTexts(ComponentMessage message) {
        List<String> texts = new ArrayList<>();
        for (Container container : message.containers()) {
            for (ContainerChild child : container.children()) {
                switch (child) {
                    case TextBlock textBlock -> texts.add(textBlock.text());
                    case Section section -> texts.addAll(section.texts());
                    default -> {
                    }
                }
            }
        }
        return texts;
    }
}
