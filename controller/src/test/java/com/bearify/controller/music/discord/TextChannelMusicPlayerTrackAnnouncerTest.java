package com.bearify.controller.music.discord;

import com.bearify.controller.music.domain.MusicPlayerTrackAnnouncer;
import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.EmbedMessage;
import com.bearify.discord.api.gateway.Guild;
import com.bearify.discord.api.gateway.SentMessage;
import com.bearify.discord.api.gateway.TextChannel;
import com.bearify.discord.api.voice.AudioProvider;
import com.bearify.discord.api.voice.VoiceSession;
import com.bearify.discord.api.voice.VoiceSessionListener;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.TrackMetadata;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TextChannelMusicPlayerTrackAnnouncerTest {

    @Test
    void acceptPostsEmbedWhenTrackStartsAndNoMessageExists() {
        AtomicReference<EmbedMessage> sent = new AtomicReference<>();
        MusicPlayerTrackAnnouncer announcer = new TextChannelMusicPlayerTrackAnnouncer(
                discordClient(sent, new AtomicInteger(), new AtomicReference<>()),
                new AnnouncerProperties("#FFA500", "#FF4444", "Bearify"),
                "text-1");

        announcer.accept(trackStart("player-1"));

        assertThat(sent.get()).isNotNull();
    }

    @Test
    void acceptUpdatesExistingMessageWhenTrackErrorArrives() {
        AtomicReference<EmbedMessage> updated = new AtomicReference<>();
        AtomicInteger sends = new AtomicInteger();
        MusicPlayerTrackAnnouncer announcer = new TextChannelMusicPlayerTrackAnnouncer(
                discordClient(new AtomicReference<>(), sends, updated),
                new AnnouncerProperties("#FFA500", "#FF4444", "Bearify"),
                "text-1");

        announcer.accept(trackStart("player-1"));
        announcer.accept(trackError("player-1"));

        assertThat(sends.get()).isEqualTo(1);
        assertThat(updated.get()).isNotNull();
    }

    @Test
    void acceptDeletesExistingMessageOnStopped() {
        AtomicReference<EmbedMessage> updated = new AtomicReference<>();
        AtomicInteger sends = new AtomicInteger();
        AtomicInteger deletes = new AtomicInteger();
        MusicPlayerTrackAnnouncer announcer = new TextChannelMusicPlayerTrackAnnouncer(
                discordClient(new AtomicReference<>(), sends, updated, deletes),
                new AnnouncerProperties("#FFA500", "#FF4444", "Bearify"),
                "text-1");

        announcer.accept(trackStart("player-1"));
        announcer.accept(new MusicPlayerEvent.Stopped("player-1", "req-2", "guild-1"));

        assertThat(sends.get()).isEqualTo(1);
        assertThat(deletes.get()).isEqualTo(1);
    }

    private static MusicPlayerEvent.TrackStart trackStart(String playerId) {
        return new MusicPlayerEvent.TrackStart(playerId, "req-1", "guild-1",
                new TrackMetadata("Song", "Artist", "https://example.com", 60_000), "@user");
    }

    private static MusicPlayerEvent.TrackError trackError(String playerId) {
        return new MusicPlayerEvent.TrackError(playerId, "req-2", "guild-1",
                new TrackMetadata("Song", "Artist", "https://example.com", 60_000));
    }

    private static DiscordClient discordClient(AtomicReference<EmbedMessage> sent,
                                               AtomicInteger sends,
                                               AtomicReference<EmbedMessage> updated) {
        return discordClient(sent, sends, updated, new AtomicInteger());
    }

    private static DiscordClient discordClient(AtomicReference<EmbedMessage> sent,
                                               AtomicInteger sends,
                                               AtomicReference<EmbedMessage> updated,
                                               AtomicInteger deletes) {
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
                    public SentMessage send(EmbedMessage embed) {
                        sent.set(embed);
                        sends.incrementAndGet();
                        return new SentMessage() {
                            @Override
                            public void delete() {
                                deletes.incrementAndGet();
                            }

                            @Override
                            public void update(EmbedMessage newEmbed) {
                                updated.set(newEmbed);
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
}
