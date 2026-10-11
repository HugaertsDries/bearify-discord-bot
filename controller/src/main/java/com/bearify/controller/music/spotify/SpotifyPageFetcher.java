package com.bearify.controller.music.spotify;

import java.net.URI;
import java.util.Optional;

/**
 * Fetches the HTML of a public Spotify page, or nothing when it can't be read.
 */
public interface SpotifyPageFetcher {

    Optional<String> fetch(URI uri);
}
