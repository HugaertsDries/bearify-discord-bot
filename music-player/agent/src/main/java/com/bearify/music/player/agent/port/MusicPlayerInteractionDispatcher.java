package com.bearify.music.player.agent.port;

import com.bearify.music.player.agent.domain.*;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class MusicPlayerInteractionDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(MusicPlayerInteractionDispatcher.class);

    private final String playerId;

    private final VoiceConnectionManager manager;
    private final AudioPlayerPool pool;
    private final MusicPlayerEventDispatcher eventDispatcher;

    MusicPlayerInteractionDispatcher(VoiceConnectionManager manager,
                                     AudioPlayerPool pool,
                                     MusicPlayerEventDispatcher eventDispatcher,
                                     @Value("${player.id}") String playerId) {
        this.playerId = playerId;
        this.manager = manager;
        this.pool = pool;
        this.eventDispatcher = eventDispatcher;
    }

    public void handle(MusicPlayerInteraction interaction) {
        switch (interaction) {
            case MusicPlayerInteraction.Connect connect ->
                    manager.connect(new ConnectionRequest(connect.requestId(), connect.voiceChannelId(), connect.guildId()));
            case MusicPlayerInteraction.Stop stop ->
                    manager.disconnect(stop.guildId());
            case MusicPlayerInteraction.Play play ->
                    loadAndPlay(play);
            case MusicPlayerInteraction.TogglePause p ->
                    pool.get(p.guildId()).ifPresentOrElse(
                            player -> player.togglePause(p.requestId()),
                            () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, p.requestId(), p.guildId())));
            case MusicPlayerInteraction.Next n ->
                    pool.get(n.guildId()).ifPresentOrElse(
                            player -> player.next(n.requestId()),
                            () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, n.requestId(), n.guildId())));
            case MusicPlayerInteraction.Previous p ->
                    pool.get(p.guildId()).ifPresentOrElse(
                            player -> player.previous(p.requestId()),
                            () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, p.requestId(), p.guildId())));
            case MusicPlayerInteraction.Rewind r ->
                    pool.get(r.guildId()).ifPresentOrElse(
                            player -> player.rewind(Duration.ofMillis(r.seekMs())),
                            () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, r.requestId(), r.guildId())));
            case MusicPlayerInteraction.Forward f ->
                    pool.get(f.guildId()).ifPresentOrElse(
                            player -> player.forward(Duration.ofMillis(f.seekMs()), f.requestId()),
                            () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, f.requestId(), f.guildId())));
            case MusicPlayerInteraction.Clear c ->
                    pool.get(c.guildId()).ifPresent(AudioPlayer::clear);
        }
    }

    private void loadAndPlay(MusicPlayerInteraction.Play play) {
        var player = pool.getOrCreate(play.guildId());
        AudioTrackLoader loader = pool.getLoader(play.guildId());
        var trackRequest = play.trackRequest();
        loader.load(trackRequest.query(), trackRequest.requesterTag(), new AudioTrackLoader.AudioTrackLoadCallback() {
            @Override
            public void trackLoaded(Track track) {
                player.play(track);
            }

            @Override
            public void playlistLoaded(Track firstTrack) {
                player.play(firstTrack);
            }

            @Override
            public void noMatches() {
                eventDispatcher.dispatch(new MusicPlayerEvent.TrackNotFound(
                        playerId, play.requestId(), play.guildId(), trackRequest.query()));
            }

            @Override
            public void loadFailed(String message) {
                LOG.warn("Failed to load track '{}': {}", trackRequest.query(), message);
                eventDispatcher.dispatch(new MusicPlayerEvent.TrackLoadFailed(
                        playerId, play.requestId(), play.guildId(), message));
            }
        });
    }
}
