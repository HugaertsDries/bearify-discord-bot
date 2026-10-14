package com.bearify.controller.music.discord;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayableMessageTest {

    private static final String YOUTUBE = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    private static final String YOUTU_BE = "https://youtu.be/dQw4w9WgXcQ";
    private static final String SPOTIFY = "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT";
    private static final String SOUNDCLOUD = "https://soundcloud.com/rick/never";
    private static final String BANDCAMP = "https://artist.bandcamp.com/track/song";
    private static final String TWITCH = "https://www.twitch.tv/somechannel";
    private static final String VIMEO = "https://vimeo.com/123456";

    // happy path

    @Test
    void extractsSupportedLinksInOrder() {
        String content = "first " + SOUNDCLOUD + " then " + YOUTUBE + ", " + SPOTIFY + " and " + BANDCAMP;

        assertThat(PlayableMessage.links(content)).containsExactly(SOUNDCLOUD, YOUTUBE, SPOTIFY, BANDCAMP);
    }

    @Test
    void searchesTextWhenNoLinks() {
        assertThat(PlayableMessage.links("we should listen to Bohemian Rhapsody")).isEmpty();
        assertThat(PlayableMessage.searchText("we should listen to Bohemian Rhapsody")).contains("we should listen to Bohemian Rhapsody");
    }

    // capabilities

    @Test
    void capsLinksAtFive() {
        String content = String.join(" ", YOUTUBE, YOUTU_BE, SPOTIFY, SOUNDCLOUD, BANDCAMP, TWITCH, VIMEO);

        assertThat(PlayableMessage.links(content)).containsExactly(YOUTUBE, YOUTU_BE, SPOTIFY, SOUNDCLOUD, BANDCAMP);
    }

    @Test
    void ignoresTextWhenLinksPresent() {
        assertThat(PlayableMessage.links("this one slaps " + YOUTU_BE)).containsExactly(YOUTU_BE);
    }

    @Test
    void ignoresUnsupportedHosts() {
        String content = "https://example.com/song.mp3 http://localhost:6379/x https://notyoutube.com/watch " + VIMEO;

        assertThat(PlayableMessage.links(content)).containsExactly(VIMEO);
    }

    @Test
    void stripsTrailingPunctuationFromLinks() {
        String content = "(" + YOUTU_BE + "), [song](" + SOUNDCLOUD + ")! <" + VIMEO + "> " + TWITCH + "?";

        assertThat(PlayableMessage.links(content)).containsExactly(YOUTU_BE, SOUNDCLOUD, VIMEO, TWITCH);
    }

    @Test
    void stripsMentionsAndCustomEmojiFromSearchText() {
        String content = "<@123> <@!456> <@&789> check <#101> this <:bear:202> out <a:dance:303> https://example.com/x";

        assertThat(PlayableMessage.searchText(content)).contains("check this out");
    }

    // edge cases

    @Test
    void truncatesSearchTextAt100Characters() {
        String content = "a".repeat(150);

        assertThat(PlayableMessage.searchText(content)).contains("a".repeat(100));
    }

    @Test
    void cutsSearchTextBeforeAnEmojiInsteadOfSplittingIt() {
        String content = "a".repeat(99) + "🎵 tail";

        assertThat(PlayableMessage.searchText(content)).contains("a".repeat(99));
    }

    @Test
    void returnsNothingForEmptyOrLinkOnlyUnsupportedMessages() {
        assertThat(PlayableMessage.links("")).isEmpty();
        assertThat(PlayableMessage.searchText("")).isEmpty();
        assertThat(PlayableMessage.links("https://example.com/song.mp3 <@123> <:bear:202>")).isEmpty();
        assertThat(PlayableMessage.searchText("https://example.com/song.mp3 <@123> <:bear:202>")).isEmpty();
    }
}
