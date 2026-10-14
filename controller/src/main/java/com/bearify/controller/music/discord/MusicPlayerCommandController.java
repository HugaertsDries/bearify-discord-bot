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

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static com.bearify.controller.music.discord.AnsweringListener.NO_PLAYER_MESSAGE;
import static com.bearify.controller.music.discord.MusicPlayerPlayback.CONNECT_FAILED_MESSAGE;
import static com.bearify.controller.music.discord.MusicPlayerPlayback.JOINING_MESSAGE;
import static com.bearify.controller.music.discord.MusicPlayerPlayback.queuedMessage;
import static com.bearify.controller.music.discord.MusicPlayerPlayback.requireVoiceSession;
@DiscordController
@InteractionGroup(value = "player", description = "Player commands")
public class MusicPlayerCommandController {

    private static final String UNAVAILABLE_MESSAGE = BearifyEmoji.BLOCKED + " | No music bears are free right now. Try again in a moment.";
    private static final String LEAVING_MESSAGE = BearifyEmoji.HONEY + " | Alright, cleaning up after myself. Talk to you later! " + BearifyEmoji.WAVE;
    private static final String LEAVE_OTHER_CHANNEL_MESSAGE = BearifyEmoji.BLOCKED + " | Bear with me, I can't lumber out of your voice channel when I'm not with you.";
    private static final String NEGATIVE_REWIND_MESSAGE = BearifyEmoji.REWIND + " | Use a positive number of seconds, bear with me.";
    private static final String NEGATIVE_FORWARD_MESSAGE = BearifyEmoji.FORWARD + " | Use a positive number of seconds, bear with me.";
    private static final String VOLUME_OUT_OF_RANGE_MESSAGE = BearifyEmoji.BLOCKED + " | Volume goes from 0 to 100, bear with me.";
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
    private final MusicPlayerPlayback playback;

    public MusicPlayerCommandController(MusicPlayerPool pool, SpotifyLinkResolver spotify) {
        this.pool = pool;
        this.playback = new MusicPlayerPlayback(pool, spotify);
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
        playback.play(interaction, List.of(query), (queued, failed) -> queuedMessage(queued.getFirst()));
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
                                // someone may still be reading the queue, so it stays until they dismiss it
                                case QueueCard.Card card -> message.keep().edit(card.message());
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

    private static String joinedMessage(String channelId) {
        return BearifyEmoji.HONEY + " | Joined <#" + channelId + ">, " + JOIN_ENDINGS.get(RANDOM.nextInt(JOIN_ENDINGS.size()));
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
        return BearifyEmoji.TRASH + " | Removed **" + PlaybackComponent.escaped(track.title()) + "** from the queue.";
    }

    private static String shuffledMessage(int count) {
        return BearifyEmoji.SHUFFLE + " | Shuffled " + count + (count == 1 ? " track" : " tracks") + ". Chaos, bear-style.";
    }

    private static String loopMessage(LoopMode mode) {
        return BearifyEmoji.REPEAT + " | You " + DiscordPlaybackAnnouncer.loopAction(mode) + ".";
    }

    private static String volumeMessage(int volume) {
        return BearifyEmoji.SPEAKER + " | Volume set to " + volume + "%. Only too loud for you? Right-click the bear → User Volume.";
    }

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
