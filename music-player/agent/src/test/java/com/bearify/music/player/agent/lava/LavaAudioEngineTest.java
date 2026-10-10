package com.bearify.music.player.agent.lava;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LavaAudioEngineTest {

    private final DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();

    @AfterEach
    void shutdownManager() {
        manager.shutdown();
    }

    @Test
    void destroyKeepsSharedManagerRunning() throws Exception {
        var destroyed = new LavaAudioEngine(manager);
        var other = new LavaAudioEngine(manager);

        destroyed.destroy();

        // a running manager without sources answers noMatches; a shut down one never finishes the load
        List<String> outcomes = new ArrayList<>();
        manager.loadItem("unknown", new RecordingLoadHandler(outcomes)).get(5, TimeUnit.SECONDS);
        assertThat(outcomes).containsExactly("noMatches");
        assertThat(manager.createPlayer()).isNotNull();
        assertThat(other.getPlayingTrack()).isEmpty();
    }

    private record RecordingLoadHandler(List<String> outcomes) implements AudioLoadResultHandler {
        @Override public void trackLoaded(AudioTrack track) { outcomes.add("trackLoaded"); }
        @Override public void playlistLoaded(AudioPlaylist playlist) { outcomes.add("playlistLoaded"); }
        @Override public void noMatches() { outcomes.add("noMatches"); }
        @Override public void loadFailed(FriendlyException exception) { outcomes.add("loadFailed"); }
    }
}
