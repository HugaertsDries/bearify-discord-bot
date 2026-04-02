package com.bearify.controller.dev;

import com.bearify.controller.music.discord.PlaybackComponentState;
import com.bearify.music.player.bridge.model.TrackMetadata;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class PlaybackAnnouncerPresets {

    private static final Map<PlaybackAnnouncerPreset, PlaybackComponentState> PRESETS = Map.of(
            PlaybackAnnouncerPreset.BROADCAST,
            PlaybackComponentState.builder()
                    .playbackState(PlaybackComponentState.PlaybackState.PAUSED)
                    .notification("Last track skipped by @Bearable")
                    .track(new TrackMetadata("Hans Zimmer: F1, The Movie Theme", "Gilles Nuytens", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", 634_000))
                    .requesterTag("@The Neighbours Kid")
                    .upNext(List.of(
                            new TrackMetadata("Hotel California", "Eagles", "https://example.com/hotel-california", 390_000, "https://img.youtube.com/vi/dQw4w9WgXcQ/maxresdefault.jpg"),
                            new TrackMetadata("Stairway to Heaven", "Led Zeppelin", "https://example.com/stairway", 482_000),
                            new TrackMetadata("Comfortably Numb", "Pink Floyd", "https://example.com/comfortably-numb", 384_000)
                    ))
                    .artworkUri(URI.create("https://img.youtube.com/vi/dQw4w9WgXcQ/maxresdefault.jpg"))
                    .paused(true)
                    .build(),
            PlaybackAnnouncerPreset.JUST_SKIPPED,
            PlaybackComponentState.builder()
                    .playbackState(PlaybackComponentState.PlaybackState.PLAYING)
                    .notification("Last track skipped by @Bearable")
                    .track(new TrackMetadata("Daft Punk - Voyager", "Daft Punk", "https://example.com/voyager", 227_000))
                    .requesterTag("@Bearable")
                    .upNext(List.of(new TrackMetadata("Digital Love", "Daft Punk", "https://example.com/digital-love", 301_000)))
                    .artworkUri(URI.create("https://img.youtube.com/vi/dQw4w9WgXcQ/maxresdefault.jpg"))
                    .paused(false)
                    .build(),
            PlaybackAnnouncerPreset.HEAVY_QUEUE,
            PlaybackComponentState.builder()
                    .playbackState(PlaybackComponentState.PlaybackState.PLAYING)
                    .track(new TrackMetadata("Live Forever", "Oasis", "https://example.com/live-forever", 276_000))
                    .requesterTag("@Queue Master")
                    .upNext(List.of(
                            new TrackMetadata("Go Your Own Way", "Fleetwood Mac", "https://example.com/go-your-own-way", 216_000),
                            new TrackMetadata("Dream On", "Aerosmith", "https://example.com/dream-on", 267_000),
                            new TrackMetadata("Kashmir", "Led Zeppelin", "https://example.com/kashmir", 515_000),
                            new TrackMetadata("Paranoid Android", "Radiohead", "https://example.com/paranoid-android", 387_000)
                    ))
                    .artworkUri(URI.create("https://img.youtube.com/vi/dQw4w9WgXcQ/maxresdefault.jpg"))
                    .paused(false)
                    .build()
    );

    public PlaybackComponentState get(PlaybackAnnouncerPreset preset) {
        return PRESETS.getOrDefault(preset, PRESETS.get(PlaybackAnnouncerPreset.BROADCAST));
    }
}
