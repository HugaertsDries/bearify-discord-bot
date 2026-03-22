package com.bearify.controller.music.discord;

import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.controller.music.domain.MusicPlayerEventListener;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.EditableMessage;
import com.bearify.discord.spring.annotation.Command;
import com.bearify.discord.spring.annotation.Interaction;
import com.bearify.discord.spring.annotation.Option;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Command(value = "player", description = "Player commands")
public class MusicPlayerCommand {

    private static final String NOT_IN_VOICE_CHANNEL_MESSAGE = "🛎️ | Looks like you're not in a voice channel! Jump in one and try again.";
    private static final String NOT_IN_GUILD_MESSAGE = "🛎️ | I only lumber around inside a server.";
    private static final String JOINING_MESSAGE = "🍯 | Sending a bear your way!";
    private static final String UNAVAILABLE_MESSAGE = "🛎️ | No music bears are free right now. Try again in a moment.";
    private static final String CONNECT_FAILED_MESSAGE = "🛎️ | The bear couldn't reach your channel. Try again in a moment.";
    private static final String LEAVING_MESSAGE = "🍯 | Alright, cleaning up after myself. Talk to you later! 👋";
    private static final String NO_PLAYER_MESSAGE = "🛎️ | Seems like I'm not even playing songs! If you'd like me to, have you tried the `/player play` command?";
    private static final String LONELY_CHANNEL_MESSAGE = "🛎️ | Bearly any bears left! They're all busy right now. Try again in a moment.";
    private static final String TRACK_NOT_FOUND_MESSAGE = "🛎️ | The bears came back empty-pawed. Try a different search or link?";
    private static final String TRACK_LOAD_FAILED_MESSAGE = "🛎️ | Oops! Something went wrong loading this track. This could be due to age-restrictions or region lock. (I'm technically less than 1 year old)";
    private static final String QUEUE_EMPTY_MESSAGE = "🍯 | The honey pot is empty. No more tracks!";
    private static final String NOTHING_TO_GO_BACK_MESSAGE = "🍯 | This is where the trail begins. Nothing to go back to!";

    private static final List<String> JOIN_ENDINGS = List.of(
            "Let's just chill for a bit?",
            "Let's have some fun!",
            "Let's get ready to rumble!! 🏁",
            "Let's party hardy! 🎉",
            "Let's get this honey flowing! 🍯",
            "Time to paws and enjoy some music! 🐾",
            "Let's make some noise in the forest! 🌲",
            "Bear with me while I get the tunes going! 🎵",
            "Ready to make the forest rumble! 🐻",
            "Let's hibernate to some good beats! 🎶"
    );
    private static final Random RANDOM = new Random();

    private final MusicPlayerPool pool;

    public MusicPlayerCommand(MusicPlayerPool pool) {
        this.pool = pool;
    }

    @SuppressWarnings("unused")
    @Interaction(value = "join", description = "I'll gladly join you")
    public void join(CommandInteraction interaction) {
        requireVoiceSession(interaction).ifPresent(ctx -> {
            pool.acquire(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(player -> {
                EditableMessage message = interaction.defer();
                message.edit(JOINING_MESSAGE);
                player.join(new MusicPlayerEventListener() {
                    public void onReady() { message.edit(joinedMessage(ctx.voiceChannelId())); }
                    public void onFailed(String reason) { message.edit(CONNECT_FAILED_MESSAGE); }
                });
            }, () -> interaction.defer().edit(UNAVAILABLE_MESSAGE));
        });
    }

    @SuppressWarnings("unused")
    @Interaction(value = "leave", description = "I'll leave the voice and clear the current playlist")
    public void leave(CommandInteraction interaction) {
        requireVoiceSession(interaction).ifPresent(ctx -> {
            pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(player -> {
                player.stop();
                interaction.reply(LEAVING_MESSAGE).send();
            }, () -> replyNoPlayer(interaction, ctx.guildId()));
        });
    }

    @SuppressWarnings("unused")
    @Interaction(value = "play", description = "I'll gladly play you some tunes. I accept links: YouTube, Twitch, Soundcloud, etc.")
    public void play(CommandInteraction interaction,
                     @Option(name = "search", description = "Search or link to a song or playlist.", required = true) String query) {
        requireVoiceSession(interaction).ifPresent(ctx -> {
            String textChannelId = interaction.getTextChannelId()
                    .orElseThrow(() -> new IllegalStateException("No text channel ID on interaction"));
            String resolvedQuery = query.startsWith("http://") || query.startsWith("https://") ? query : "ytsearch:" + query;
            String userMention = interaction.getUserMention();

            pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                player -> {
                    EditableMessage msg = interaction.defer();
                    player.play(resolvedQuery, textChannelId, new MusicPlayerEventListener() {
                        public void onTrackNotFound(String q) { msg.edit(TRACK_NOT_FOUND_MESSAGE); }
                        public void onTrackLoadFailed(String reason) { msg.edit(TRACK_LOAD_FAILED_MESSAGE); }
                    });
                    msg.edit(trackQueuedMessage(userMention));
                },
                () -> pool.acquire(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                    player -> {
                        EditableMessage message = interaction.defer();
                        message.edit(JOINING_MESSAGE);
                        player.join(new MusicPlayerEventListener() {
                            public void onReady() {
                                player.play(resolvedQuery, textChannelId, new MusicPlayerEventListener() {
                                    public void onTrackNotFound(String q) { message.edit(TRACK_NOT_FOUND_MESSAGE); }
                                    public void onTrackLoadFailed(String reason) { message.edit(TRACK_LOAD_FAILED_MESSAGE); }
                                });
                                message.edit(trackQueuedMessage(userMention));
                            }
                            public void onFailed(String reason) {
                                message.edit(CONNECT_FAILED_MESSAGE);
                            }
                        });
                    },
                    () -> interaction.defer().edit(UNAVAILABLE_MESSAGE)
                )
            );
        });
    }

    @SuppressWarnings("unused")
    @Interaction(value = "pause", description = "I'll pause/continue the current track")
    public void pause(CommandInteraction interaction) {
        requireVoiceSession(interaction).ifPresent(ctx ->
            pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                player -> {
                    String userMention = interaction.getUserMention();
                    EditableMessage msg = interaction.defer();
                    player.togglePause(new MusicPlayerEventListener() {
                        public void onPaused()  { msg.edit(pausedMessage(userMention)); }
                        public void onResumed() { msg.edit(resumedMessage(userMention)); }
                        public void onFailed(String reason) { msg.edit(NO_PLAYER_MESSAGE); }
                    });
                },
                () -> replyNoPlayer(interaction, ctx.guildId())
            )
        );
    }

    @SuppressWarnings("unused")
    @Interaction(value = "previous", description = "I'll go back to the previous track")
    public void previous(CommandInteraction interaction) {
        requireVoiceSession(interaction).ifPresent(ctx ->
            pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                player -> {
                    String userMention = interaction.getUserMention();
                    EditableMessage msg = interaction.defer();
                    player.previous(new MusicPlayerEventListener() {
                        public void onNothingToGoBack() { msg.edit(NOTHING_TO_GO_BACK_MESSAGE); }
                    });
                    msg.edit(previousMessage(userMention));
                },
                () -> replyNoPlayer(interaction, ctx.guildId())
            )
        );
    }

    @SuppressWarnings("unused")
    @Interaction(value = "next", description = "I'll skip the current song")
    public void next(CommandInteraction interaction) {
        requireVoiceSession(interaction).ifPresent(ctx ->
            pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                player -> {
                    String userMention = interaction.getUserMention();
                    EditableMessage msg = interaction.defer();
                    player.next(new MusicPlayerEventListener() {
                        public void onQueueEmpty() { msg.edit(QUEUE_EMPTY_MESSAGE); }
                    });
                    msg.edit(skippedMessage(userMention));
                },
                () -> replyNoPlayer(interaction, ctx.guildId())
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
                    player.rewind(Duration.ofSeconds(seconds));
                    interaction.reply(rewoundMessage(interaction.getUserMention(), seconds)).send();
                },
                () -> replyNoPlayer(interaction, ctx.guildId())
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
                    String userMention = interaction.getUserMention();
                    EditableMessage msg = interaction.defer();
                    player.forward(Duration.ofSeconds(seconds), new MusicPlayerEventListener() {
                        public void onQueueEmpty() { msg.edit(QUEUE_EMPTY_MESSAGE); }
                    });
                    msg.edit(forwardedMessage(userMention, seconds));
                },
                () -> replyNoPlayer(interaction, ctx.guildId())
            )
        );
    }

    @SuppressWarnings("unused")
    @Interaction(value = "clear", description = "I'll clear the current playlist. (I'm not responsible for losing friends)")
    public void clear(CommandInteraction interaction) {
        requireVoiceSession(interaction).ifPresent(ctx ->
            pool.find(ctx.guildId(), ctx.voiceChannelId()).ifPresentOrElse(
                player -> {
                    player.clear();
                    interaction.reply(clearedMessage(interaction.getUserMention())).send();
                },
                () -> replyNoPlayer(interaction, ctx.guildId())
            )
        );
    }

    private void replyNoPlayer(CommandInteraction interaction, String guildId) {
        String message = pool.hasActiveSessionFor(guildId) ? LONELY_CHANNEL_MESSAGE : NO_PLAYER_MESSAGE;
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
        return "🍯 | Joined <#" + channelId + ">, " + JOIN_ENDINGS.get(RANDOM.nextInt(JOIN_ENDINGS.size()));
    }

    private static String trackQueuedMessage(String userMention) {
        return "🕐 | " + userMention + " added your track to the mix.";
    }

    private static String pausedMessage(String userMention) {
        return "⏸️ | " + userMention + " paused the current track.";
    }

    private static String resumedMessage(String userMention) {
        return "▶️ | " + userMention + " resumed the current track.";
    }

    private static String skippedMessage(String userMention) {
        return "⏭️ | " + userMention + " skipped the current track.";
    }

    private static String previousMessage(String userMention) {
        return "⏮️ | " + userMention + " went back to the previous track.";
    }

    private static String rewoundMessage(String userMention, long seconds) {
        return seconds > 0
                ? "⏪ | " + userMention + " rewound the current track by " + seconds + "s."
                : "⏪ | " + userMention + " rewound the current track.";
    }

    private static String forwardedMessage(String userMention, long seconds) {
        return seconds > 0
                ? "⏩ | " + userMention + " forwarded the current track by " + seconds + "s."
                : "⏩ | " + userMention + " forwarded the current track.";
    }

    private static String clearedMessage(String userMention) {
        return "🗑️ | " + userMention + " cleared the playlist.";
    }

    private record ChannelContext(String guildId, String voiceChannelId) {}
}
