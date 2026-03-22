package com.bearify.discord.api.voice;

import java.io.Closeable;

/**
 * Abstraction over an audio source that provides PCM or Opus-encoded audio frames.
 * Implementations wrap an underlying audio engine (e.g. LavaPlayer) and are registered
 * with a {@link com.bearify.discord.api.gateway.Guild} before audio playback begins.
 */
public interface AudioProvider extends Closeable {

    /** Default no-op close — override to release resources. */
    @Override
    default void close() {}

    /** Returns true if there is audio data ready to be sent this frame. */
    boolean canProvide();

    /** Returns 20ms of audio data (Opus-encoded if {@link #isOpus()} returns true). */
    byte[] provide20MsAudio();

    /** Returns true if the audio data is Opus-encoded; false if raw PCM. */
    boolean isOpus();
}
