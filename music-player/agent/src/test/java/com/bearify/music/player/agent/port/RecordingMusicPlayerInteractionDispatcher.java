package com.bearify.music.player.agent.port;

import com.bearify.music.player.agent.domain.AudioPlayerPool;
import com.bearify.music.player.agent.domain.VoiceConnectionManager;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingMusicPlayerInteractionDispatcher extends MusicPlayerInteractionDispatcher {

    private final List<MusicPlayerInteraction> interactions = new CopyOnWriteArrayList<>();

    public RecordingMusicPlayerInteractionDispatcher(VoiceConnectionManager manager,
                                                     AudioPlayerPool pool,
                                                     MusicPlayerEventDispatcher eventDispatcher,
                                                     String playerId) {
        super(manager, pool, eventDispatcher, playerId);
    }

    @Override
    public void handle(MusicPlayerInteraction interaction) {
        interactions.add(interaction);
        super.handle(interaction);
    }

    public List<MusicPlayerInteraction> interactions() {
        return List.copyOf(interactions);
    }

    public void reset() {
        interactions.clear();
    }
}
