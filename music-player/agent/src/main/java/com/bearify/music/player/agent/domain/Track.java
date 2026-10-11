package com.bearify.music.player.agent.domain;

import com.bearify.music.player.bridge.model.TrackMetadata;

/**
 * Abstraction over a playable audio track. Decouples domain logic from LavaPlayer's {@code AudioTrack}.
 */
public interface Track {

    String uri();

    String title();
    String author();

    long duration();
    long position();
    void setPosition(long positionMs);

    /** False for live streams: they have no fixed timeline to seek in. */
    boolean isSeekable();

    /** The Discord mention string (e.g. {@code <@123456>}) of the user who requested this track. May be null. */
    String requesterTag();

    Track clone();

    default TrackMetadata toMetadata() {
        return new TrackMetadata(title(), author(), uri(), duration());
    }
}
