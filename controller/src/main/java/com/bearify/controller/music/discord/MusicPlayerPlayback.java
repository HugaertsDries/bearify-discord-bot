package com.bearify.controller.music.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerEventListener;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.controller.music.spotify.SpotifyLinkResolver;
import com.bearify.discord.api.interaction.EditableMessage;
import com.bearify.discord.api.interaction.RepliableInteraction;
import com.bearify.music.player.bridge.model.TrackMetadata;
import com.bearify.music.player.bridge.model.TrackRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The play flow behind {@code /player play} and the "Play with Bearify" message command: checks the user is in voice,
 * resolves Spotify links, summons a bear when none is in the channel, queues the queries and edits the reply once.
 */
final class MusicPlayerPlayback {

    static final String NOT_IN_VOICE_CHANNEL_MESSAGE = BearifyEmoji.BLOCKED + " | Looks like you're not in a voice channel! Jump in one and try again.";
    static final String NOT_IN_GUILD_MESSAGE = BearifyEmoji.BLOCKED + " | I only lumber around inside a server.";
    static final String JOINING_MESSAGE = BearifyEmoji.HONEY + " | Sending a bear your way!";
    static final String CONNECT_FAILED_MESSAGE = BearifyEmoji.BLOCKED + " | The bear couldn't reach your channel. Try again in a moment.";
    private static final String PLAY_UNAVAILABLE_MESSAGE = BearifyEmoji.BLOCKED + " | Bearly any bears left! They're all busy right now. Try again in a moment.";
    private static final String TRACK_NOT_FOUND_MESSAGE = BearifyEmoji.BLOCKED + " | I couldn't sniff out that track. Try a different search or link?";
    private static final String TRACK_LOAD_FAILED_MESSAGE = BearifyEmoji.BLOCKED + " | Oops! Something went wrong loading this track. This could be due to age-restrictions or region lock. (I'm technically less than 1 year old)";
    private static final String SPOTIFY_NOT_A_SONG_MESSAGE = BearifyEmoji.BLOCKED + " | I can only sniff out single Spotify songs. Paste a track link instead!";
    private static final String SPOTIFY_UNREADABLE_MESSAGE = BearifyEmoji.BLOCKED + " | I couldn't sniff out that Spotify song. Try a YouTube link or a search instead?";
    private static final String NONE_PLAYED_MESSAGE = BearifyEmoji.BLOCKED + " | None of those links could be played.";

    private final MusicPlayerPool pool;
    private final SpotifyLinkResolver spotify;

    MusicPlayerPlayback(MusicPlayerPool pool, SpotifyLinkResolver spotify) {
        this.pool = pool;
        this.spotify = spotify;
    }

    /**
     * Queues the queries in order. When none of them queues, the reply is the failure itself for a single query, or a
     * "none of those" for several; otherwise {@code reply} words what was queued.
     */
    void play(RepliableInteraction interaction, List<String> queries, PlayReply reply) {
        requireVoiceSession(interaction).ifPresent(ctx -> {
            String textChannelId = interaction.getTextChannelId()
                    .orElseThrow(() -> new IllegalStateException("No text channel ID on interaction"));
            // before resolving: reading a Spotify page can outlast Discord's 3s acknowledgement window
            EditableMessage message = interaction.defer(true);
            List<String> failures = new ArrayList<>();
            List<TrackRequest> requests = queries.stream()
                    .flatMap(query -> resolveQuery(query, failures).stream())
                    .map(query -> new TrackRequest(query, textChannelId, interaction.getUserMention()))
                    .toList();
            Sequence sequence = new Sequence(requests, queries.size(), failures, message, reply);
            if (requests.isEmpty()) {
                sequence.finish();
                return;
            }

            pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                    sequence::playOn,
                    () -> {
                        var player = pool.acquire(ctx.guildId(), ctx.voiceChannelId());
                        message.edit(JOINING_MESSAGE);
                        player.join(new MusicPlayerEventListener() {
                            @Override
                            public void onReady() {
                                sequence.playOn(player);
                            }

                            @Override
                            public void onFailed(String reason) {
                                message.edit(CONNECT_FAILED_MESSAGE);
                            }

                            @Override
                            public void onNoPlayersAvailable() {
                                message.edit(PLAY_UNAVAILABLE_MESSAGE);
                            }
                        });
                    }
            );
        });
    }

    static Optional<ChannelContext> requireVoiceSession(RepliableInteraction interaction) {
        if (interaction.getVoiceChannelId().isEmpty()) {
            interaction.reply(NOT_IN_VOICE_CHANNEL_MESSAGE).ephemeral().send();
            return Optional.empty();
        }
        if (interaction.getGuildId().isEmpty()) {
            interaction.reply(NOT_IN_GUILD_MESSAGE).ephemeral().send();
            return Optional.empty();
        }
        return Optional.of(new ChannelContext(
                interaction.getGuildId().orElseThrow(),
                interaction.getVoiceChannelId().orElseThrow()));
    }

    static String queuedMessage(PlayReply.Queued queued) {
        if (queued.count() > 1) {
            return BearifyEmoji.MUSIC + " | Queued **" + queued.count() + "** tracks from the playlist.";
        }
        return BearifyEmoji.MUSIC + " | Queued **" + queued.track().title() + "**.";
    }

    private Optional<String> resolveQuery(String query, List<String> failures) {
        return switch (spotify.resolve(query)) {
            case SpotifyLinkResolver.NotSpotify() ->
                    Optional.of(PlayableMessage.isLink(query) ? query : "ytsearch:" + query);
            case SpotifyLinkResolver.Resolved(String searchQuery) -> Optional.of("ytsearch:" + searchQuery);
            case SpotifyLinkResolver.NotASong() -> {
                failures.add(SPOTIFY_NOT_A_SONG_MESSAGE);
                yield Optional.empty();
            }
            case SpotifyLinkResolver.Unreadable() -> {
                failures.add(SPOTIFY_UNREADABLE_MESSAGE);
                yield Optional.empty();
            }
        };
    }

    record ChannelContext(String guildId, String voiceChannelId) {}

    /** Words the reply once at least one query queued; failures have their own texts. */
    @FunctionalInterface
    interface PlayReply {
        /** {@code queued} holds what each query that worked queued, in order; {@code failed} counts the queries that didn't. */
        String queued(List<Queued> queued, int failed);

        /** {@code count} is above 1 when the query was a playlist. */
        record Queued(TrackMetadata track, int count) {}
    }

    /**
     * Sends each request only after the bear answered the one before, so they queue in the order given even though
     * the bear doesn't promise to handle requests in the order they arrive.
     */
    private static final class Sequence {

        private final List<TrackRequest> requests;
        private final int queryCount;
        private final List<String> failures;
        private final EditableMessage message;
        private final PlayReply reply;
        private final List<PlayReply.Queued> queued = new ArrayList<>();

        private Sequence(List<TrackRequest> requests, int queryCount, List<String> failures, EditableMessage message, PlayReply reply) {
            this.requests = requests;
            this.queryCount = queryCount;
            this.failures = failures;
            this.message = message;
            this.reply = reply;
        }

        void playOn(MusicPlayer player) {
            playFrom(player, 0);
        }

        private void playFrom(MusicPlayer player, int index) {
            if (index == requests.size()) {
                finish();
                return;
            }
            player.play(requests.get(index), new AnsweringListener(this::stop) {
                @Override
                public void onTrackQueued(TrackMetadata track, int queuedCount) {
                    queued.add(new PlayReply.Queued(track, queuedCount));
                    playFrom(player, index + 1);
                }

                @Override
                public void onTrackNotFound(String query) {
                    failures.add(TRACK_NOT_FOUND_MESSAGE);
                    playFrom(player, index + 1);
                }

                @Override
                public void onTrackLoadFailed(String reason) {
                    failures.add(TRACK_LOAD_FAILED_MESSAGE);
                    playFrom(player, index + 1);
                }
            });
        }

        // the bear didn't answer or is gone, so the requests after this one would end the same way
        private void stop(String failure) {
            failures.add(failure);
            finish();
        }

        void finish() {
            if (queued.isEmpty()) {
                message.edit(queryCount == 1 ? failures.getFirst() : NONE_PLAYED_MESSAGE);
            } else {
                message.edit(reply.queued(List.copyOf(queued), queryCount - queued.size()));
            }
        }
    }
}
