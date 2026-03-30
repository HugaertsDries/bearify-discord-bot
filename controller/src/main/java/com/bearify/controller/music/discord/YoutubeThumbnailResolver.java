package com.bearify.controller.music.discord;

import com.bearify.music.player.bridge.model.TrackMetadata;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YoutubeThumbnailResolver {

    private static final Pattern YOUTUBE_ID = Pattern.compile("(?:v=|youtu\\.be/)([A-Za-z0-9_-]{11})");

    public Optional<URI> resolve(TrackMetadata track) {
        if (track.artworkUri().isPresent()) {
            return track.artworkUri();
        }
        Matcher matcher = YOUTUBE_ID.matcher(track.uriValue().toString());
        if (matcher.find()) {
            return Optional.of(URI.create("https://img.youtube.com/vi/" + matcher.group(1) + "/maxresdefault.jpg"));
        }
        return Optional.empty();
    }
}
