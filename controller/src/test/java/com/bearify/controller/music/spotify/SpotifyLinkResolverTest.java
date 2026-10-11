package com.bearify.controller.music.spotify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SpotifyLinkResolverTest {

    private static final String TRACK_LINK = "https://open.spotify.com/intl-nl/track/4cOdK2wGLETKBW3PvgPWqT?si=abc";
    private static final String SHORT_LINK = "https://spotify.link/abc123";
    private static final String TITLE = "Never Gonna Give You Up";
    private static final String DESCRIPTION = "Rick Astley · Whenever You Need Somebody · Song · 1987";

    private final List<URI> fetched = new ArrayList<>();

    // --- HAPPY PATH ---

    @Test
    void resolvesSongPageToArtistAndTitleSearch() {
        SpotifyLinkResolver resolver = resolverServing(page("music.song", TITLE, DESCRIPTION));

        assertThat(resolver.resolve(TRACK_LINK)).isEqualTo(new SpotifyLinkResolver.Resolved("Rick Astley " + TITLE));
        assertThat(fetched).containsExactly(URI.create(TRACK_LINK));
    }

    @Test
    void resolvesShortLinkThroughTheFetchedPage() {
        SpotifyLinkResolver resolver = resolverServing(page("music.song", TITLE, DESCRIPTION));

        assertThat(resolver.resolve(SHORT_LINK)).isEqualTo(new SpotifyLinkResolver.Resolved("Rick Astley " + TITLE));
    }

    @Test
    void resolvesTitleOnlyWhenDescriptionMissing() {
        SpotifyLinkResolver resolver = resolverServing(Optional.of(meta("type", "music.song") + meta("title", TITLE)));

        assertThat(resolver.resolve(TRACK_LINK)).isEqualTo(new SpotifyLinkResolver.Resolved(TITLE));
    }

    @Test
    void resolvesTitleOnlyWhenDescriptionHasNoArtistSeparator() {
        SpotifyLinkResolver resolver = resolverServing(page("music.song", TITLE, "Listen to this song on Spotify."));

        assertThat(resolver.resolve(TRACK_LINK)).isEqualTo(new SpotifyLinkResolver.Resolved(TITLE));
    }

    @Test
    void readsTagsWhateverTheAttributeOrder() {
        SpotifyLinkResolver resolver = resolverServing(Optional.of(
                "<meta content=\"music.song\" property=\"og:type\">"
                        + "<meta data-x=\"1\" content=\"" + TITLE + "\" property=\"og:title\" />"
                        + "<meta content=\"" + DESCRIPTION + "\" property=\"og:description\"/>"));

        assertThat(resolver.resolve(TRACK_LINK)).isEqualTo(new SpotifyLinkResolver.Resolved("Rick Astley " + TITLE));
    }

    @Test
    void decodesHtmlEntitiesInTitle() {
        SpotifyLinkResolver resolver = resolverServing(page("music.song", "Don&#x27;t Stop Me Now &amp; More &#233;", "Queen · Jazz · Song · 1978"));

        assertThat(resolver.resolve(TRACK_LINK)).isEqualTo(new SpotifyLinkResolver.Resolved("Queen Don't Stop Me Now & More é"));
    }

    @Test
    void passesNonSpotifyQueriesThrough() {
        SpotifyLinkResolver resolver = resolverServing(page("music.song", TITLE, DESCRIPTION));

        assertThat(resolver.resolve("never gonna give you up")).isEqualTo(new SpotifyLinkResolver.NotSpotify());
        assertThat(resolver.resolve("https://youtube.com/watch?v=dQw4w9WgXcQ")).isEqualTo(new SpotifyLinkResolver.NotSpotify());
        assertThat(fetched).isEmpty();
    }

    // --- EDGE CASES ---

    @ParameterizedTest
    @ValueSource(strings = {"music.album", "music.playlist", "music.musician", "video.episode", "profile"})
    void reportsAlbumAndPlaylistPagesAsNotASong(String type) {
        SpotifyLinkResolver resolver = resolverServing(page(type, "Whenever You Need Somebody", DESCRIPTION));

        assertThat(resolver.resolve(TRACK_LINK)).isEqualTo(new SpotifyLinkResolver.NotASong());
    }

    @Test
    void reportsUnreadableWhenFetchFails() {
        SpotifyLinkResolver resolver = resolverServing(Optional.empty());

        assertThat(resolver.resolve(TRACK_LINK)).isEqualTo(new SpotifyLinkResolver.Unreadable());
    }

    @Test
    void reportsUnreadableWhenTitleMissing() {
        SpotifyLinkResolver resolver = resolverServing(Optional.of("<html><head><title>Spotify</title></head></html>"));

        assertThat(resolver.resolve(TRACK_LINK)).isEqualTo(new SpotifyLinkResolver.Unreadable());
    }

    @Test
    void reportsUnreadableWhenLinkIsMalformed() {
        SpotifyLinkResolver resolver = resolverServing(page("music.song", TITLE, DESCRIPTION));

        assertThat(resolver.resolve("https://open.spotify.com/track/abc def")).isEqualTo(new SpotifyLinkResolver.Unreadable());
        assertThat(fetched).isEmpty();
    }

    @Test
    void keepsOutOfRangeEntitiesAsWritten() {
        SpotifyLinkResolver resolver = resolverServing(page("music.song", "Song &#99999999999;", DESCRIPTION));

        assertThat(resolver.resolve(TRACK_LINK)).isEqualTo(new SpotifyLinkResolver.Resolved("Rick Astley Song &#99999999999;"));
    }

    // --- HELPERS ---

    private SpotifyLinkResolver resolverServing(Optional<String> html) {
        return new SpotifyLinkResolver(uri -> {
            fetched.add(uri);
            return html;
        });
    }

    private static Optional<String> page(String type, String title, String description) {
        return Optional.of("<html><head>" + meta("title", title) + meta("description", description) + meta("type", type) + "</head></html>");
    }

    private static String meta(String property, String content) {
        return "<meta property=\"og:" + property + "\" content=\"" + content + "\"/>";
    }
}
