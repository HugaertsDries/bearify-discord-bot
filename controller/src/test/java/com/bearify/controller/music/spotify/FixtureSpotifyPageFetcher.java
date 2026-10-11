package com.bearify.controller.music.spotify;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

/**
 * Serves fixture Spotify pages so no integration test reaches the real open.spotify.com.
 */
public class FixtureSpotifyPageFetcher implements SpotifyPageFetcher {

    public static final String TRACK_LINK = "https://open.spotify.com/intl-nl/track/4cOdK2wGLETKBW3PvgPWqT?si=abc";
    public static final String PLAYLIST_LINK = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M";
    public static final String UNREADABLE_LINK = "https://spotify.link/gone";

    private static final Map<String, String> PAGES = Map.of(
            TRACK_LINK, "<meta property=\"og:title\" content=\"Never Gonna Give You Up\"/>"
                    + "<meta property=\"og:description\" content=\"Rick Astley · Whenever You Need Somebody · Song · 1987\"/>"
                    + "<meta property=\"og:type\" content=\"music.song\"/>",
            PLAYLIST_LINK, "<meta property=\"og:title\" content=\"Today's Top Hits\"/>"
                    + "<meta property=\"og:type\" content=\"music.playlist\"/>");

    @Override
    public Optional<String> fetch(URI uri) {
        return Optional.ofNullable(PAGES.get(uri.toString()));
    }
}
