package com.bearify.music.player.agent;

import com.bearify.music.player.agent.domain.VoiceConnectionManager;
import com.bearify.music.player.bridge.events.MusicPlayerInteraction;
import com.bearify.music.player.bridge.model.StopReason;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class RecordingVoiceConnectionManager extends VoiceConnectionManager {

    private final List<MusicPlayerInteraction.Connect> calls = new ArrayList<>();
    private final Set<String> disconnectedGuilds = new HashSet<>();
    private final Map<String, StopReason> stopReasons = new HashMap<>();

    public RecordingVoiceConnectionManager() {
        super(null, null, null, null, null, "test", null);  // null args safe: connect/disconnect are fully overridden
    }

    @Override
    public void connect(MusicPlayerInteraction.Connect request) {
        calls.add(request);
    }

    @Override
    public void disconnect(String guildId, StopReason reason) {
        disconnect(guildId, "", reason);
    }

    @Override
    public void disconnect(String guildId, String requestId, StopReason reason) {
        disconnectedGuilds.add(guildId);
        stopReasons.put(guildId, reason);
    }

    @Override
    public void close() {
    }

    public List<MusicPlayerInteraction.Connect> getCalls() {
        return List.copyOf(calls);
    }

    public Set<String> getDisconnectedGuilds() {
        return Collections.unmodifiableSet(disconnectedGuilds);
    }

    public Optional<StopReason> stopReason(String guildId) {
        return Optional.ofNullable(stopReasons.get(guildId));
    }

    public boolean isDisconnected() {
        return !disconnectedGuilds.isEmpty();
    }

    public void reset() {
        calls.clear();
        disconnectedGuilds.clear();
        stopReasons.clear();
    }
}
