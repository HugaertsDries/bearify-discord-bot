package com.bearify.music.player.agent;

import com.bearify.music.player.agent.domain.ConnectionRequest;
import com.bearify.music.player.agent.domain.VoiceConnectionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecordingVoiceConnectionManager extends VoiceConnectionManager {

    private final List<ConnectionRequest> calls = new ArrayList<>();
    private final Set<String> disconnectedGuilds = new HashSet<>();

    public RecordingVoiceConnectionManager() {
        super(null, null, "test");
    }

    @Override
    public void connect(ConnectionRequest request) {
        calls.add(request);
    }

    @Override
    public void disconnect(String guildId) {
        disconnectedGuilds.add(guildId);
    }

    public List<ConnectionRequest> getCalls() {
        return List.copyOf(calls);
    }

    public Set<String> getDisconnectedGuilds() {
        return Collections.unmodifiableSet(disconnectedGuilds);
    }

    public boolean isDisconnected() {
        return !disconnectedGuilds.isEmpty();
    }

    public void reset() {
        calls.clear();
        disconnectedGuilds.clear();
    }
}
