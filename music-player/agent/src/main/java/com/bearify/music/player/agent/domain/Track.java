package com.bearify.music.player.agent.domain;

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

    Track clone();
}
