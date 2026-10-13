package com.bearify.controller.music.discord;

import com.bearify.controller.music.spotify.SpotifyLinkResolver;
import com.bearify.music.player.bridge.protocol.TrustedLinks;

import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/** Picks what to play out of a chat message: its supported links, or else its text as a search. */
final class PlayableMessage {

    static final int MAX_LINKS = 5;
    static final int MAX_SEARCH_LENGTH = 100;

    private static final Pattern LINK = Pattern.compile("https?://[^\\s<>]+");
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[).,!?\\]>]+$");
    // user, role and channel mentions, and custom emoji such as <:bear:123> or <a:dance:456>
    private static final Pattern MENTION_OR_EMOJI = Pattern.compile("<(@[!&]?|#)\\d+>|<a?:\\w+:\\d+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private PlayableMessage() {
    }

    /** The links the bear plays (the hosts it trusts, plus Spotify songs) in message order, at most {@value MAX_LINKS}. */
    static List<String> links(String content) {
        return LINK.matcher(content).results()
                .map(MatchResult::group)
                .map(link -> TRAILING_PUNCTUATION.matcher(link).replaceFirst(""))
                .filter(link -> TrustedLinks.isTrusted(link) || SpotifyLinkResolver.isSpotifyLink(link))
                .limit(MAX_LINKS)
                .toList();
    }

    /** The text without links, mentions and custom emoji, cut to {@value MAX_SEARCH_LENGTH} characters; empty when nothing is left. */
    static Optional<String> searchText(String content) {
        String text = LINK.matcher(content).replaceAll(" ");
        text = MENTION_OR_EMOJI.matcher(text).replaceAll(" ");
        text = WHITESPACE.matcher(text).replaceAll(" ").strip();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(text.substring(0, Math.min(text.length(), MAX_SEARCH_LENGTH)).strip());
    }
}
