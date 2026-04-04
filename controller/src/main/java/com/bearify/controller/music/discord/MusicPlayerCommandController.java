package com.bearify.controller.music.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.controller.music.domain.MusicPlayerEventListener;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.EditableMessage;
import com.bearify.discord.spring.annotation.DiscordController;
import com.bearify.discord.spring.annotation.Interaction;
import com.bearify.discord.spring.annotation.InteractionGroup;
import com.bearify.discord.spring.annotation.Option;
import com.bearify.music.player.bridge.model.TrackRequest;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;

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
    private static final String NO_PLAYER_MESSAGE = BearifyEmoji.BLOCKED + " | Seems like I'm not even playing songs! If you'd like me to, have you tried the `/player play` command?";
    private static final String LEAVE_OTHER_CHANNEL_MESSAGE = BearifyEmoji.BLOCKED + " | Bear with me, I can't lumber out of your voice channel when I'm not with you.";
    private static final String TRACK_NOT_FOUND_MESSAGE = BearifyEmoji.BLOCKED + " | I couldn't sniff out that track. Try a different search or link?";
    private static final String TRACK_LOAD_FAILED_MESSAGE = BearifyEmoji.BLOCKED + " | Oops! Something went wrong loading this track. This could be due to age-restrictions or region lock. (I'm technically less than 1 year old)";
    private static final String QUEUE_EMPTY_MESSAGE = BearifyEmoji.HONEY + " | This is where the trail ends. Try adding more using `/player play` command!";
    private static final String NOTHING_TO_GO_BACK_MESSAGE = BearifyEmoji.HONEY + " | This is where the trail begins. Nothing to go back to!";

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

    public MusicPlayerCommandController(MusicPlayerPool pool) {
        this.pool = pool;
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
                    player.stop();
                    interaction.reply(LEAVING_MESSAGE).send();
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
            String resolvedQuery = query.startsWith("http://") || query.startsWith("https://") ? query : "ytsearch:" + query;
            String userMention = interaction.getUserMention();
            var trackRequest = new TrackRequest(resolvedQuery, textChannelId, userMention);

            pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                    player -> {
                        EditableMessage message = interaction.defer(true);
                        player.play(trackRequest, new MusicPlayerEventListener() {
                            @Override
                            public void onTrackNotFound(String q) {
                                message.edit(TRACK_NOT_FOUND_MESSAGE);
                            }

                            @Override
                            public void onTrackLoadFailed(String reason) {
                                message.edit(TRACK_LOAD_FAILED_MESSAGE);
                            }
                        });
                        message.edit(trackQueuedMessage(userMention));
                    },
                    () -> {
                        var player = pool.acquire(ctx.guildId(), ctx.voiceChannelId());
                        EditableMessage message = interaction.defer(true);
                        message.edit(JOINING_MESSAGE);
                        player.join(new MusicPlayerEventListener() {
                            @Override
                            public void onReady() {
                                player.play(trackRequest, new MusicPlayerEventListener() {
                                    @Override
                                    public void onTrackNotFound(String q) {
                                        message.edit(TRACK_NOT_FOUND_MESSAGE);
                                    }

                                    @Override
                                    public void onTrackLoadFailed(String reason) {
                                        message.edit(TRACK_LOAD_FAILED_MESSAGE);
                                    }
                                });
                                message.edit(trackQueuedMessage(userMention));
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
                            player.togglePause(interaction.getUserMention(), new MusicPlayerEventListener() {
                                @Override
                                public void onPaused() {
                                    message.edit(pausedMessage());
                                }

                                @Override
                                public void onResumed() {
                                    message.edit(resumedMessage());
                                }

                                @Override
                                public void onFailed(String reason) {
                                    message.edit(NO_PLAYER_MESSAGE);
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
                            player.previous(interaction.getUserMention(), new MusicPlayerEventListener() {
                                @Override
                                public void onNothingToGoBack() {
                                    message.edit(NOTHING_TO_GO_BACK_MESSAGE);
                                }
                            });
                            message.edit(previousMessage());
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
                            player.next(interaction.getUserMention(), new MusicPlayerEventListener() {
                                @Override
                                public void onNothingToAdvance() {
                                    message.edit(QUEUE_EMPTY_MESSAGE);
                                }
                            });
                            message.edit(nextMessage());
                        },
                        () -> replyNoPlayer(interaction)
                )
        );
    }

    @SuppressWarnings("unused")
    @Interaction(value = "rewind", description = "I'll rewind the current track for you")
    public void rewind(CommandInteraction interaction,
                       @Option(name = "seconds", description = "The duration I should rewind back in seconds", defaultValue = "0") long seconds) {
        requireVoiceSession(interaction).ifPresent(ctx ->
                pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                        player -> {
                            player.rewind(Duration.ofSeconds(seconds), interaction.getUserMention());
                            interaction.reply(rewoundMessage(seconds)).ephemeral().send();
                        },
                        () -> replyNoPlayer(interaction)
                )
        );
    }

    @SuppressWarnings("unused")
    @Interaction(value = "forward", description = "I'll forward the current track for you")
    public void forward(CommandInteraction interaction,
                        @Option(name = "seconds", description = "The duration I should skip forward in seconds", defaultValue = "0") long seconds) {
        requireVoiceSession(interaction).ifPresent(ctx ->
                pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                        player -> {
                            EditableMessage message = interaction.defer(true);
                            player.forward(Duration.ofSeconds(seconds), interaction.getUserMention(), new MusicPlayerEventListener() {
                                @Override
                                public void onNothingToAdvance() {
                                    message.edit(QUEUE_EMPTY_MESSAGE);
                                }
                            });
                            message.edit(forwardedMessage(seconds));
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
                            player.clear(interaction.getUserMention());
                            interaction.reply(clearedMessage()).ephemeral().send();
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

    private static String joinedMessage(String channelId) {
        return BearifyEmoji.HONEY + " | Joined <#" + channelId + ">, " + JOIN_ENDINGS.get(RANDOM.nextInt(JOIN_ENDINGS.size()));
    }

    private static String trackQueuedMessage(String userMention) {
        return BearifyEmoji.CLOCK + " | " + userMention + " added your track to the mix.";
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

    private static String rewoundMessage(long seconds) {
        if (seconds == 0) {
            return BearifyEmoji.REWIND + " | Bold strategy. Rewinding by exactly 0 seconds.";
        }
        return BearifyEmoji.REWIND + " | Rewound the current track by " + seconds + "s for you.";
    }

    private static String forwardedMessage(long seconds) {
        if (seconds == 0) {
            return BearifyEmoji.FORWARD + " | Bold strategy. Forwarding by exactly 0 seconds.";
        }
        return BearifyEmoji.FORWARD + " | Forwarded the current track by " + seconds + "s for you.";
    }

    private static String clearedMessage() {
        return BearifyEmoji.TRASH + " | Cleared the playlist. Fresh paws.";
    }

    private record ChannelContext(String guildId, String voiceChannelId) {}
}
