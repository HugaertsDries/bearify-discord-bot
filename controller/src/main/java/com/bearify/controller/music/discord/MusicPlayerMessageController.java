package com.bearify.controller.music.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.controller.music.discord.MusicPlayerPlayback.PlayReply;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.controller.music.spotify.SpotifyLinkResolver;
import com.bearify.discord.api.interaction.InteractionType;
import com.bearify.discord.api.interaction.MessageContextInteraction;
import com.bearify.discord.spring.annotation.DiscordController;
import com.bearify.discord.spring.annotation.Interaction;

import java.util.List;

import static com.bearify.controller.music.discord.MusicPlayerPlayback.queuedMessage;

/** "Play with Bearify" under Apps on a message: queues the message's links, or searches its text when it has none. */
@DiscordController
public class MusicPlayerMessageController {

    static final String PLAY_WITH_BEARIFY = "Play with Bearify";

    private static final String NOTHING_TO_PLAY_MESSAGE = BearifyEmoji.BLOCKED + " | Nothing to play in that message.";
    private static final String WRONG_ONE_HINT = " Wrong one? `/player remove` it.";

    private final MusicPlayerPlayback playback;

    public MusicPlayerMessageController(MusicPlayerPool pool, SpotifyLinkResolver spotify) {
        this.playback = new MusicPlayerPlayback(pool, spotify);
    }

    @SuppressWarnings("unused")
    @Interaction(type = InteractionType.MESSAGE_CONTEXT, value = PLAY_WITH_BEARIFY)
    public void play(MessageContextInteraction interaction) {
        String content = interaction.getTargetMessageContent();
        List<String> links = PlayableMessage.links(content);
        if (!links.isEmpty()) {
            playback.play(interaction, links, (queued, failed) -> linksMessage(queued, failed, interaction.getTargetAuthorMention()));
            return;
        }
        PlayableMessage.searchText(content).ifPresentOrElse(
                text -> playback.play(interaction, List.of(text), (queued, failed) -> queuedMessage(queued.getFirst()) + WRONG_ONE_HINT),
                () -> interaction.reply(NOTHING_TO_PLAY_MESSAGE).ephemeral().send());
    }

    private static String linksMessage(List<PlayReply.Queued> queued, int failed, String authorMention) {
        if (queued.size() == 1 && failed == 0) {
            return queuedMessage(queued.getFirst());
        }
        int tracks = queued.stream().mapToInt(PlayReply.Queued::count).sum();
        String message = BearifyEmoji.MUSIC + " | Queued " + tracks + (tracks == 1 ? " track" : " tracks") + " from " + authorMention + "'s message.";
        return failed == 0 ? message : message + " (" + failed + (failed == 1 ? " link" : " links") + " couldn't be played.)";
    }
}
