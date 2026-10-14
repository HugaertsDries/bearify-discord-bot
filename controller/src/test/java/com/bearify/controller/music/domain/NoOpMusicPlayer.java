package com.bearify.controller.music.domain;

import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.LoopMode;
import com.bearify.music.player.bridge.model.TrackRequest;

import java.time.Duration;
import java.util.Optional;

/** A {@link MusicPlayer} that ignores every request; test doubles override only what they answer. */
public class NoOpMusicPlayer implements MusicPlayer {
    @Override public void join(MusicPlayerEventListener handler) {}
    @Override public void stop(MusicPlayerEventListener handler) {}
    @Override public void play(TrackRequest request, MusicPlayerEventListener handler) {}
    @Override public void togglePause(String requesterTag, MusicPlayerEventListener handler) {}
    @Override public void previous(String requesterTag, MusicPlayerEventListener handler) {}
    @Override public void next(String requesterTag, MusicPlayerEventListener handler) {}
    @Override public void rewind(Duration seek, String requesterTag, MusicPlayerEventListener handler) {}
    @Override public void forward(Duration seek, String requesterTag, MusicPlayerEventListener handler) {}
    @Override public void clear(String requesterTag, MusicPlayerEventListener handler) {}
    @Override public void remove(int position, String uriPrefix, String requesterTag, MusicPlayerEventListener handler) {}
    @Override public void shuffle(String requesterTag, MusicPlayerEventListener handler) {}
    @Override public void setVolume(int volume, String requesterTag, MusicPlayerEventListener handler) {}
    @Override public void cycleLoop(String requesterTag, MusicPlayerEventListener handler) {}
    @Override public void setLoop(LoopMode mode, String requesterTag, MusicPlayerEventListener handler) {}
    @Override public void jump(Duration position, String requesterTag, MusicPlayerEventListener handler) {}
    @Override public Optional<MusicPlayerEvent> queue() { return Optional.empty(); }
}
