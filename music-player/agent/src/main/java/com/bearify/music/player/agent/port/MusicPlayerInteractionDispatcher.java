package com.bearify.music.player.agent.port;

import com.bearify.music.player.agent.domain.*;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.model.StopReason;
import com.bearify.music.player.bridge.model.TrackMetadata;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
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
                    withPlayer(p.guildId(), p.requestId(), player -> player.togglePause(p.request()));
            case MusicPlayerInteraction.Next n ->
                    withPlayer(n.guildId(), n.requestId(), player -> player.next(n.request()));
            case MusicPlayerInteraction.Previous p ->
                    withPlayer(p.guildId(), p.requestId(), player -> player.previous(p.request()));
            case MusicPlayerInteraction.Rewind r ->
                    withPlayer(r.guildId(), r.requestId(), player -> player.rewind(Duration.ofMillis(r.seekMs()), r.request()));
            case MusicPlayerInteraction.Forward f ->
                    withPlayer(f.guildId(), f.requestId(), player -> player.forward(Duration.ofMillis(f.seekMs()), f.request()));
            case MusicPlayerInteraction.Clear c ->
                    withPlayer(c.guildId(), c.requestId(), player -> player.clear(c.request()));
            case MusicPlayerInteraction.Search search ->
                    search(search);
            case MusicPlayerInteraction.HealthCheck check ->
                    healthProbe.run(check.requestId());
            case MusicPlayerInteraction.QueueRequest q ->
                    withPlayer(q.guildId(), q.requestId(), player -> player.snapshot(q.requestId()));
            case MusicPlayerInteraction.Remove r ->
                    withPlayer(r.guildId(), r.requestId(), player -> player.remove(r.position(), r.uriPrefix(), r.request()));
            case MusicPlayerInteraction.ToggleShuffle s ->
                    withPlayer(s.guildId(), s.requestId(), player -> player.toggleShuffle(s.request()));
            case MusicPlayerInteraction.SetVolume v ->
                    withPlayer(v.guildId(), v.requestId(), player -> player.setVolume(v.volume(), v.request()));
            case MusicPlayerInteraction.CycleLoop l ->
                    withPlayer(l.guildId(), l.requestId(), player -> player.cycleLoop(l.request()));
            case MusicPlayerInteraction.SetLoop l ->
                    withPlayer(l.guildId(), l.requestId(), player -> player.setLoop(l.mode(), l.request()));
            case MusicPlayerInteraction.Jump j ->
                    withPlayer(j.guildId(), j.requestId(), player -> player.jump(Duration.ofMillis(j.positionMs()), j.request()));
        }
    }

    // a guild without a player still answers, so the controller isn't left waiting
    private void withPlayer(String guildId, String requestId, Consumer<AudioPlayer> action) {
        pool.get(guildId).ifPresentOrElse(action,
                () -> eventDispatcher.dispatch(new MusicPlayerEvent.PlayerNotFound(playerId, requestId, guildId)));
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
