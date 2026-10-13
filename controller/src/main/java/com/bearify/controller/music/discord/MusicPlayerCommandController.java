package com.bearify.controller.music.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.controller.music.domain.MusicPlayerEventListener;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.controller.music.domain.Timestamp;
import com.bearify.controller.music.spotify.SpotifyLinkResolver;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.EditableMessage;
import com.bearify.discord.spring.annotation.DiscordController;
import com.bearify.discord.spring.annotation.Interaction;
import com.bearify.discord.spring.annotation.InteractionGroup;
import com.bearify.discord.spring.annotation.Option;
import com.bearify.music.player.bridge.model.LoopMode;
import com.bearify.music.player.bridge.model.TrackMetadata;
import com.bearify.music.player.bridge.model.TrackRequest;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static com.bearify.controller.music.discord.AnsweringListener.NO_PLAYER_MESSAGE;
@DiscordController
@InteractionGroup(value = "player", description = "Player commands")
public class MusicPlayerCommandController {

    private static final String NOT_IN_VOICE_CHANNEL_MESSAGE = BearifyEmoji.BLOCKED + " | Looks like you're not in a voice channel! Jump in one and try again.";
    private static final String NOT_IN_GUILD_MESSAGE = BearifyEmoji.BLOCKED + " | I only lumber around inside a server.";
    private static final String JOINING_MESSAGE = BearifyEmoji.HONEY + " | Sending a bear your way!";
    private static final String UNAVAILABLE_MESSAGE = BearifyEmoji.BLOCKED + " | No music bears are free right now. Try again in a moment.";
    private static final String PLAY_UNAVAILABLE_MESSAGE = BearifyEmoji.BLOCKED + " | Bearly any bears left! They're all busy right now. Try again in a moment.";
    private static final String CONNECT_FAILED_MESSAGE = BearifyEmoji.BLOCKED + " | The bear couldn't reach your channel. Try again in a moment.";
    private static final String LEAVING_MESSAGE = BearifyEmoji.HONEY + " | Alright, cleaning up after myself. Talk to you later! " + BearifyEmoji.WAVE;
    private static final String LEAVE_OTHER_CHANNEL_MESSAGE = BearifyEmoji.BLOCKED + " | Bear with me, I can't lumber out of your voice channel when I'm not with you.";
    private static final String TRACK_NOT_FOUND_MESSAGE = BearifyEmoji.BLOCKED + " | I couldn't sniff out that track. Try a different search or link?";
    private static final String NEGATIVE_REWIND_MESSAGE = BearifyEmoji.REWIND + " | Use a positive number of seconds, bear with me.";
    private static final String NEGATIVE_FORWARD_MESSAGE = BearifyEmoji.FORWARD + " | Use a positive number of seconds, bear with me.";
    private static final String VOLUME_OUT_OF_RANGE_MESSAGE = BearifyEmoji.BLOCKED + " | Volume goes from 0 to 100, bear with me.";
    private static final String TRACK_LOAD_FAILED_MESSAGE = BearifyEmoji.BLOCKED + " | Oops! Something went wrong loading this track. This could be due to age-restrictions or region lock. (I'm technically less than 1 year old)";
    private static final String SPOTIFY_NOT_A_SONG_MESSAGE = BearifyEmoji.BLOCKED + " | I can only sniff out single Spotify songs. Paste a track link instead!";
    private static final String SPOTIFY_UNREADABLE_MESSAGE = BearifyEmoji.BLOCKED + " | I couldn't sniff out that Spotify song. Try a YouTube link or a search instead?";
    private static final String PICK_A_TRACK_MESSAGE = BearifyEmoji.BLOCKED + " | Pick a track from the list so I know which one to drop.";
    private static final String TRACK_NOT_IN_QUEUE_MESSAGE = BearifyEmoji.BLOCKED + " | That track isn't in the queue anymore. Run `/player queue` to see what's left.";
    private static final String NOTHING_TO_SHUFFLE_MESSAGE = BearifyEmoji.HONEY + " | The queue is empty, nothing to shuffle.";
    private static final String LOOP_MODE_MESSAGE = BearifyEmoji.BLOCKED + " | Pick a loop mode: `off`, `queue` or `track`.";
    private static final String TIME_FORMAT_MESSAGE = BearifyEmoji.BLOCKED + " | Use a time like `1:23`, `1h30m` or `83` (seconds).";

    private static final List<String> JOIN_ENDINGS = List.of(
            "Let's just chill for a bit?",
            "Let's have some fun!",
            "Let's get ready to rumble!! " + BearifyEmoji.FLAG,
            "Let's party hardy! " + BearifyEmoji.PARTY,
            "Let's get this honey flowing! " + BearifyEmoji.HONEY,
            "Time to paws and enjoy some music! " + BearifyEmoji.PAW,
            "Let's make some noise in the forest! " + BearifyEmoji.FOREST,
            "Bear with me while I get the tunes going! " + BearifyEmoji.MUSIC,
            "Ready to make the forest rumble! " + BearifyEmoji.BEAR,
            "Let's hibernate to some good beats! " + BearifyEmoji.NOTES
    );
    private static final Random RANDOM = new Random();

    private final MusicPlayerPool pool;
    private final SpotifyLinkResolver spotify;

    public MusicPlayerCommandController(MusicPlayerPool pool, SpotifyLinkResolver spotify) {
        this.pool = pool;
        this.spotify = spotify;
    }

    @SuppressWarnings("unused")
    @Interaction(value = "join", description = "I'll gladly join you")
    public void join(CommandInteraction interaction) {
        requireVoiceSession(interaction).ifPresent(ctx -> {
            var player = pool.acquire(ctx.guildId(), ctx.voiceChannelId());
            EditableMessage message = interaction.defer();
            message.edit(JOINING_MESSAGE);
            player.join(new MusicPlayerEventListener() {
                @Override
                public void onReady() {
                    message.edit(joinedMessage(ctx.voiceChannelId()));
                }

                @Override
                public void onFailed(String reason) {
                    message.edit(CONNECT_FAILED_MESSAGE);
                }

                @Override
                public void onNoPlayersAvailable() {
                    message.edit(UNAVAILABLE_MESSAGE);
                }
            });
        });
    }

    @SuppressWarnings("unused")
    @Interaction(value = "leave", description = "I'll leave the voice and clear the current playlist")
    public void leave(CommandInteraction interaction) {
        requireVoiceSession(interaction).ifPresent(ctx ->
                pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(player -> {
                    EditableMessage message = interaction.defer();
                    player.stop(new AnsweringListener(message::edit) {
                        @Override
                        public void onStopped() {
                            answer(LEAVING_MESSAGE);
                        }
                    });
                }, () -> replyNoPlayerForLeave(interaction, ctx.guildId()))
        );
    }

    @SuppressWarnings("unused")
    @Interaction(value = "play", description = "I'll gladly play you some tunes. I accept links: YouTube, Twitch, Soundcloud, etc.")
    public void play(CommandInteraction interaction,
                     @Option(name = "search", description = "Search or link to a song or playlist.", required = true, autocomplete = true) String query) {
        requireVoiceSession(interaction).ifPresent(ctx -> {
            String textChannelId = interaction.getTextChannelId()
                    .orElseThrow(() -> new IllegalStateException("No text channel ID on interaction"));
            // before resolving: reading a Spotify page can outlast Discord's 3s acknowledgement window
            EditableMessage message = interaction.defer(true);
            Optional<String> resolvedQuery = resolveQuery(query, message);
            if (resolvedQuery.isEmpty()) {
                return;
            }
            var trackRequest = new TrackRequest(resolvedQuery.get(), textChannelId, interaction.getUserMention());

            pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                    player -> player.play(trackRequest, queueing(message)),
                    () -> {
                        var player = pool.acquire(ctx.guildId(), ctx.voiceChannelId());
                        message.edit(JOINING_MESSAGE);
                        player.join(new MusicPlayerEventListener() {
                            @Override
                            public void onReady() {
                                player.play(trackRequest, queueing(message));
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

    @SuppressWarnings("unused")
    @Interaction(value = "pause", description = "I'll pause/continue the current track")
    public void pause(CommandInteraction interaction) {
        requireVoiceSession(interaction).ifPresent(ctx ->
                pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                        player -> {
                            EditableMessage message = interaction.defer(true);
                            player.togglePause(interaction.getUserMention(), new AnsweringListener(message::edit) {
                                @Override
                                public void onPaused() {
                                    answer(pausedMessage());
                                }

                                @Override
                                public void onResumed() {
                                    answer(resumedMessage());
                                }
                            });
                        },
                        () -> replyNoPlayer(interaction)
                )
        );
    }

    @SuppressWarnings("unused")
    @Interaction(value = "previous", description = "I'll go back to the previous track")
    public void previous(CommandInteraction interaction) {
        requireVoiceSession(interaction).ifPresent(ctx ->
                pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                        player -> {
                            EditableMessage message = interaction.defer(true);
                            player.previous(interaction.getUserMention(), new AnsweringListener(message::edit) {
                                @Override
                                public void onWentBack() {
                                    answer(previousMessage());
                                }

                                @Override
                                public void onRestarted() {
                                    answer(restartedMessage());
                                }
                            });
                        },
                        () -> replyNoPlayer(interaction)
                )
        );
    }

    @SuppressWarnings("unused")
    @Interaction(value = "next", description = "I'll skip the current song")
    public void next(CommandInteraction interaction) {
        requireVoiceSession(interaction).ifPresent(ctx ->
                pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                        player -> {
                            EditableMessage message = interaction.defer(true);
                            player.next(interaction.getUserMention(), new AnsweringListener(message::edit) {
                                @Override
                                public void onSkipped() {
                                    answer(nextMessage());
                                }
                            });
                        },
                        () -> replyNoPlayer(interaction)
                )
        );
    }

    @SuppressWarnings("unused")
    @Interaction(value = "rewind", description = "I'll rewind the current track for you")
    public void rewind(CommandInteraction interaction,
                       @Option(name = "seconds", description = "The duration I should rewind back in seconds", defaultValue = "0") long seconds) {
        if (seconds < 0) {
            interaction.reply(NEGATIVE_REWIND_MESSAGE).ephemeral().send();
            return;
        }
        requireVoiceSession(interaction).ifPresent(ctx ->
                pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                        player -> {
                            EditableMessage message = interaction.defer(true);
                            player.rewind(Duration.ofSeconds(seconds), interaction.getUserMention(), new AnsweringListener(message::edit) {
                                @Override
                                public void onRewound(Duration seek) {
                                    answer(rewoundMessage(seek));
                                }
                            });
                        },
                        () -> replyNoPlayer(interaction)
                )
        );
    }

    @SuppressWarnings("unused")
    @Interaction(value = "forward", description = "I'll forward the current track for you")
    public void forward(CommandInteraction interaction,
                        @Option(name = "seconds", description = "The duration I should skip forward in seconds", defaultValue = "0") long seconds) {
        if (seconds < 0) {
            interaction.reply(NEGATIVE_FORWARD_MESSAGE).ephemeral().send();
            return;
        }
        requireVoiceSession(interaction).ifPresent(ctx ->
                pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                        player -> {
                            EditableMessage message = interaction.defer(true);
                            player.forward(Duration.ofSeconds(seconds), interaction.getUserMention(), new AnsweringListener(message::edit) {
                                @Override
                                public void onForwarded(Duration seek) {
                                    answer(forwardedMessage(seek));
                                }

                                @Override
                                public void onSkipped() {
                                    answer(nextMessage());
                                }
                            });
                        },
                        () -> replyNoPlayer(interaction)
                )
        );
    }

    @SuppressWarnings("unused")
    @Interaction(value = "jump", description = "I'll jump straight to a moment in the current track")
    public void jump(CommandInteraction interaction,
                     @Option(name = "time", description = "Where to jump to, like 1:23, 1h30m or 83 (seconds)", required = true) String time) {
        Optional<Duration> position = Timestamp.parse(time).map(Timestamp::position);
        if (position.isEmpty()) {
            interaction.reply(TIME_FORMAT_MESSAGE).ephemeral().send();
            return;
        }
        requireVoiceSession(interaction).ifPresent(ctx ->
                pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                        player -> {
                            EditableMessage message = interaction.defer(true);
                            player.jump(position.get(), interaction.getUserMention(), new AnsweringListener(message::edit) {
                                @Override
                                public void onJumped(Duration jumpedTo) {
                                    answer(jumpedMessage(jumpedTo));
                                }

                                @Override
                                public void onJumpOutOfRange(Duration trackLength) {
                                    answer(trackTooShortMessage(trackLength));
                                }
                            });
                        },
                        () -> replyNoPlayer(interaction)
                )
        );
    }

    @SuppressWarnings("unused")
    @Interaction(value = "clear", description = "I'll clear the current playlist. (I'm not responsible for losing friends)")
    public void clear(CommandInteraction interaction) {
        requireVoiceSession(interaction).ifPresent(ctx ->
                pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                        player -> {
                            EditableMessage message = interaction.defer(true);
                            player.clear(interaction.getUserMention(), new AnsweringListener(message::edit) {
                                @Override
                                public void onCleared() {
                                    answer(clearedMessage());
                                }
                            });
                        },
                        () -> replyNoPlayer(interaction)
                )
        );
    }

    @SuppressWarnings("unused")
    @Interaction(value = "remove", description = "I'll drop one track from the queue")
    public void remove(CommandInteraction interaction,
                       @Option(name = "track", description = "The queued track to drop", required = true, autocomplete = true) String track) {
        Optional<PickedTrack> picked = PickedTrack.parse(track);
        if (picked.isEmpty()) {
            interaction.reply(PICK_A_TRACK_MESSAGE).ephemeral().send();
            return;
        }
        requireVoiceSession(interaction).ifPresent(ctx ->
                pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                        player -> {
                            EditableMessage message = interaction.defer(true);
                            player.remove(picked.get().position(), picked.get().uriPrefix(), interaction.getUserMention(), new AnsweringListener(message::edit) {
                                @Override
                                public void onRemoved(TrackMetadata removed) {
                                    answer(removedMessage(removed));
                                }

                                @Override
                                public void onTrackNotInQueue() {
                                    answer(TRACK_NOT_IN_QUEUE_MESSAGE);
                                }
                            });
                        },
                        () -> replyNoPlayer(interaction)
                )
        );
    }

    @SuppressWarnings("unused")
    @Interaction(value = "shuffle", description = "I'll mix up the queue")
    public void shuffle(CommandInteraction interaction) {
        requireVoiceSession(interaction).ifPresent(ctx ->
                pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                        player -> {
                            EditableMessage message = interaction.defer(true);
                            player.shuffle(interaction.getUserMention(), new AnsweringListener(message::edit) {
                                @Override
                                public void onShuffled(int count) {
                                    answer(count == 0 ? NOTHING_TO_SHUFFLE_MESSAGE : shuffledMessage(count));
                                }
                            });
                        },
                        () -> replyNoPlayer(interaction)
                )
        );
    }

    @SuppressWarnings("unused")
    @Interaction(value = "volume", description = "I'll turn the music up or down for everyone")
    public void volume(CommandInteraction interaction,
                       @Option(name = "level", description = "The volume from 0 to 100", required = true) int level) {
        if (level < 0 || level > 100) {
            interaction.reply(VOLUME_OUT_OF_RANGE_MESSAGE).ephemeral().send();
            return;
        }
        requireVoiceSession(interaction).ifPresent(ctx ->
                pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                        player -> {
                            EditableMessage message = interaction.defer(true);
                            player.setVolume(level, interaction.getUserMention(), new AnsweringListener(message::edit) {
                                @Override
                                public void onVolumeChanged(int volume) {
                                    answer(volumeMessage(volume));
                                }
                            });
                        },
                        () -> replyNoPlayer(interaction)
                )
        );
    }

    @SuppressWarnings("unused")
    @Interaction(value = "loop", description = "I'll keep the queue or this track going round")
    public void loop(CommandInteraction interaction,
                     @Option(name = "mode", description = "off, queue or track", required = true, autocomplete = true) String mode) {
        Optional<LoopMode> loopMode = Arrays.stream(LoopMode.values())
                .filter(candidate -> candidate.name().equalsIgnoreCase(mode.trim()))
                .findFirst();
        if (loopMode.isEmpty()) {
            interaction.reply(LOOP_MODE_MESSAGE).ephemeral().send();
            return;
        }
        requireVoiceSession(interaction).ifPresent(ctx ->
                pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                        player -> {
                            EditableMessage message = interaction.defer(true);
                            player.setLoop(loopMode.get(), interaction.getUserMention(), new AnsweringListener(message::edit) {
                                @Override
                                public void onLoopModeChanged(LoopMode changed) {
                                    answer(loopMessage(changed));
                                }
                            });
                        },
                        () -> replyNoPlayer(interaction)
                )
        );
    }

    @SuppressWarnings("unused")
    @Interaction(value = "queue", description = "I'll show you everything that's coming up on the trail")
    public void queue(CommandInteraction interaction) {
        requireVoiceSession(interaction).ifPresent(ctx ->
                pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                        player -> {
                            EditableMessage message = interaction.defer(true);
                            switch (QueueCard.wholeQueue(player.queue())) {
                                case QueueCard.Text text -> message.edit(text.text());
                                case QueueCard.Card card -> message.edit(card.message());
                            }
                        },
                        () -> replyNoPlayer(interaction)
                )
        );
    }

    private void replyNoPlayer(CommandInteraction interaction) {
        interaction.reply(NO_PLAYER_MESSAGE).ephemeral().send();
    }

    private void replyNoPlayerForLeave(CommandInteraction interaction, String guildId) {
        String message = pool.hasActiveSessionFor(guildId) ? LEAVE_OTHER_CHANNEL_MESSAGE : NO_PLAYER_MESSAGE;
        interaction.reply(message).ephemeral().send();
    }

    private Optional<ChannelContext> requireVoiceSession(CommandInteraction interaction) {
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

    private Optional<String> resolveQuery(String query, EditableMessage message) {
        return switch (spotify.resolve(query)) {
            case SpotifyLinkResolver.NotSpotify() ->
                    Optional.of(query.startsWith("http://") || query.startsWith("https://") ? query : "ytsearch:" + query);
            case SpotifyLinkResolver.Resolved(String searchQuery) -> Optional.of("ytsearch:" + searchQuery);
            case SpotifyLinkResolver.NotASong() -> {
                message.edit(SPOTIFY_NOT_A_SONG_MESSAGE);
                yield Optional.empty();
            }
            case SpotifyLinkResolver.Unreadable() -> {
                message.edit(SPOTIFY_UNREADABLE_MESSAGE);
                yield Optional.empty();
            }
        };
    }

    private static String joinedMessage(String channelId) {
        return BearifyEmoji.HONEY + " | Joined <#" + channelId + ">, " + JOIN_ENDINGS.get(RANDOM.nextInt(JOIN_ENDINGS.size()));
    }

    private static MusicPlayerEventListener queueing(EditableMessage message) {
        return new AnsweringListener(message::edit) {
            @Override
            public void onTrackQueued(TrackMetadata track, int queuedCount) {
                answer(queuedMessage(track, queuedCount));
            }

            @Override
            public void onTrackNotFound(String query) {
                answer(TRACK_NOT_FOUND_MESSAGE);
            }

            @Override
            public void onTrackLoadFailed(String reason) {
                answer(TRACK_LOAD_FAILED_MESSAGE);
            }
        };
    }

    private static String queuedMessage(TrackMetadata track, int queuedCount) {
        if (queuedCount > 1) {
            return BearifyEmoji.MUSIC + " | Queued **" + queuedCount + "** tracks from the playlist.";
        }
        return BearifyEmoji.MUSIC + " | Queued **" + track.title() + "**.";
    }

    private static String pausedMessage() {
        return BearifyEmoji.PAUSE + " | Paused the current track for you.";
    }

    private static String resumedMessage() {
        return BearifyEmoji.PLAY + " | Resumed the current track for you.";
    }

    private static String nextMessage() {
        return BearifyEmoji.NEXT + " | On to the next track.";
    }

    private static String previousMessage() {
        return BearifyEmoji.PREVIOUS + " | Back on the trail to the previous track.";
    }

    private static String restartedMessage() {
        return BearifyEmoji.PREVIOUS + " | Back to the start of this track.";
    }

    private static String rewoundMessage(Duration seek) {
        return BearifyEmoji.REWIND + " | Rewound the current track by " + seek.toSeconds() + "s for you.";
    }

    private static String forwardedMessage(Duration seek) {
        return BearifyEmoji.FORWARD + " | Forwarded the current track by " + seek.toSeconds() + "s for you.";
    }

    private static String jumpedMessage(Duration position) {
        return BearifyEmoji.FORWARD + " | Jumped to " + PlaybackComponent.humanReadable(position) + ".";
    }

    private static String trackTooShortMessage(Duration trackLength) {
        return BearifyEmoji.BLOCKED + " | That track is only " + PlaybackComponent.humanReadable(trackLength) + " long.";
    }

    private static String clearedMessage() {
        return BearifyEmoji.TRASH + " | Cleared the playlist. Fresh paws.";
    }

    private static String removedMessage(TrackMetadata track) {
        return BearifyEmoji.TRASH + " | Removed **" + track.title() + "** from the queue.";
    }

    private static String shuffledMessage(int count) {
        return BearifyEmoji.SHUFFLE + " | Shuffled " + count + (count == 1 ? " track" : " tracks") + ". Chaos, bear-style.";
    }

    private static String loopMessage(LoopMode mode) {
        return BearifyEmoji.REPEAT + switch (mode) {
            case OFF -> " | Loop off.";
            case QUEUE -> " | Looping the queue.";
            case TRACK -> " | Looping this track.";
        };
    }

    private static String volumeMessage(int volume) {
        return BearifyEmoji.SPEAKER + " | Volume set to " + volume + "%. Only too loud for you? Right-click the bear → User Volume.";
    }

    private record ChannelContext(String guildId, String voiceChannelId) {}

    /** The {@code /player remove} option: a suggestion's {@code <position>|<uri prefix>}, or a position typed by hand. */
    private record PickedTrack(int position, String uriPrefix) {
        static Optional<PickedTrack> parse(String value) {
            int separator = value.indexOf(MusicPlayerAutocompleteController.REMOVE_VALUE_SEPARATOR);
            String position = separator < 0 ? value.trim() : value.substring(0, separator);
            String uriPrefix = separator < 0 ? "" : value.substring(separator + 1);
            try {
                int parsed = Integer.parseInt(position);
                return parsed > 0 ? Optional.of(new PickedTrack(parsed, uriPrefix)) : Optional.empty();
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
    }
}
