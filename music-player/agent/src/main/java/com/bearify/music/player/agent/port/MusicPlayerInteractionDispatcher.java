package com.bearify.music.player.agent.port;

import com.bearify.music.player.agent.domain.*;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.model.StopReason;
import com.bearify.music.player.bridge.model.TrackMetadata;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MusicPlayerInteractionDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(MusicPlayerInteractionDispatcher.class);

    private final String playerId;

    private final VoiceConnectionManager manager;
    private final AudioPlayerPool pool;
    private final AudioTrackLoader loader;
    private final MusicPlayerEventDispatcher eventDispatcher;
    private final HealthProbe healthProbe;

    MusicPlayerInteractionDispatcher(VoiceConnectionManager manager,
                                     AudioPlayerPool pool,
                                     AudioTrackLoader loader,
                                     MusicPlayerEventDispatcher eventDispatcher,
                                     HealthProbe healthProbe,
                                     @Value("${player.id}") String playerId) {
        this.playerId = playerId;
        this.manager = manager;
        this.pool = pool;
        this.loader = loader;
        this.eventDispatcher = eventDispatcher;
        this.healthProbe = healthProbe;
    }

    public void handle(MusicPlayerInteraction interaction) {
        switch (interaction) {
            case MusicPlayerInteraction.Connect connect ->
                    manager.connect(connect);
            case MusicPlayerInteraction.Stop stop ->
                    manager.disconnect(stop.guildId(), stop.requestId(), StopReason.REQUESTED);
            case MusicPlayerInteraction.Play play ->
                    loadAndPlay(play);
            case MusicPlayerInteraction.TogglePause p ->
                    pool.get(p.guildId()).ifPresentOrElse(
                            player -> player.togglePause(p.request()),
                            () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, p.requestId(), p.guildId())));
            case MusicPlayerInteraction.Next n ->
                    pool.get(n.guildId()).ifPresentOrElse(
                            player -> player.next(n.request()),
                            () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, n.requestId(), n.guildId())));
            case MusicPlayerInteraction.Previous p ->
                    pool.get(p.guildId()).ifPresentOrElse(
                            player -> player.previous(p.request()),
                            () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, p.requestId(), p.guildId())));
            case MusicPlayerInteraction.Rewind r ->
                    pool.get(r.guildId()).ifPresentOrElse(
                            player -> player.rewind(Duration.ofMillis(r.seekMs()), r.request()),
                            () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, r.requestId(), r.guildId())));
            case MusicPlayerInteraction.Forward f ->
                    pool.get(f.guildId()).ifPresentOrElse(
                            player -> player.forward(Duration.ofMillis(f.seekMs()), f.request()),
                            () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, f.requestId(), f.guildId())));
            case MusicPlayerInteraction.Clear c ->
                    pool.get(c.guildId()).ifPresentOrElse(
                            player -> player.clear(c.request()),
                            () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, c.requestId(), c.guildId())));
            case MusicPlayerInteraction.Search search ->
                    search(search);
            case MusicPlayerInteraction.HealthCheck check ->
                    healthProbe.run(check.requestId());
            case MusicPlayerInteraction.QueueRequest q ->
                    pool.get(q.guildId()).ifPresentOrElse(
                            player -> player.snapshot(q.requestId()),
                            () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, q.requestId(), q.guildId())));
            case MusicPlayerInteraction.Remove r ->
                    pool.get(r.guildId()).ifPresentOrElse(
                            player -> player.remove(r.position(), r.uriPrefix(), r.request()),
                            () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, r.requestId(), r.guildId())));
            case MusicPlayerInteraction.Shuffle s ->
                    pool.get(s.guildId()).ifPresentOrElse(
                            player -> player.shuffle(s.request()),
                            () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, s.requestId(), s.guildId())));
            case MusicPlayerInteraction.SetVolume v ->
                    pool.get(v.guildId()).ifPresentOrElse(
                            player -> player.setVolume(v.volume(), v.request()),
                            () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, v.requestId(), v.guildId())));
        }
    }

    private void loadAndPlay(MusicPlayerInteraction.Play play) {
        // only Connect creates a player: a bear sent out of voice must not queue into a fresh, silent one
        var found = pool.get(play.guildId());
        if (found.isEmpty()) {
            eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, play.requestId(), play.guildId()));
            return;
        }
        var player = found.get();
        var trackRequest = play.trackRequest();
        loader.load(trackRequest.query(), trackRequest.requesterTag(), new AudioTrackLoader.AudioTrackLoadCallback() {
            @Override
            public void trackLoaded(Track track) {
                player.play(track);
                queued(track, 1);
            }

            @Override
            public void playlistLoaded(List<Track> tracks) {
                if (tracks.isEmpty()) {
                    noMatches();
                    return;
                }
                player.play(tracks);
                queued(tracks.getFirst(), tracks.size());
            }

            private void queued(Track first, int count) {
                eventDispatcher.dispatch(new MusicPlayerEvent.TrackQueued(playerId, play.requestId(), play.guildId(), first.toMetadata(), count));
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

    private void search(MusicPlayerInteraction.Search search) {
        loader.search("ytsearch:" + search.query(), search.limit(), new AudioTrackLoader.AudioTrackSearchCallback() {
            @Override
            public void searchResults(List<TrackMetadata> tracks) {
                eventDispatcher.dispatch(new MusicPlayerEvent.SearchResults(
                        playerId, search.requestId(), search.guildId(), tracks));
            }

            @Override
            public void noMatches() {
                eventDispatcher.dispatch(new MusicPlayerEvent.SearchResults(
                        playerId, search.requestId(), search.guildId(), List.of()));
            }

            @Override
            public void loadFailed(String message) {
                LOG.warn("Failed to search '{}': {}", search.query(), message);
                eventDispatcher.dispatch(new MusicPlayerEvent.SearchResults(
                        playerId, search.requestId(), search.guildId(), List.of()));
            }
        });
    }
}
