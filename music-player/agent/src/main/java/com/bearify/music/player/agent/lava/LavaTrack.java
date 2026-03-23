package com.bearify.music.player.agent.lava;

import com.bearify.music.player.agent.domain.Track;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

/**
 * Adapts a LavaPlayer {@link AudioTrack} to the domain {@link Track} interface.
 */
class LavaTrack implements Track {

    private final AudioTrack track;

    LavaTrack(AudioTrack track) {
        this.track = track;
    }

    @Override
    public long duration() {
        return track.getDuration();
    }

    @Override
    public long position() {
        return track.getPosition();
    }

    @Override
    public void setPosition(long positionMs) {
        track.setPosition(positionMs);
    }

    @Override
    public Track clone() {
        return new LavaTrack(track.makeClone());
    }

    @Override
    public String title() {
        return track.getInfo().title;
    }

    @Override
    public String author() {
        return track.getInfo().author;
    }

    @Override
    public String uri() {
        return track.getInfo().uri;
    }

    /** Returns the underlying LavaPlayer track (used by {@link LavaAudioEngine} for playback). */
    AudioTrack unwrap() {
        return track;
    }
}
