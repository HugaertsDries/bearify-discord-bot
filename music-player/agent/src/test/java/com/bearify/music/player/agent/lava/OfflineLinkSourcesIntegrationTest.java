package com.bearify.music.player.agent.lava;

import com.bearify.music.player.agent.AbstractAgentIntegrationTest;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class OfflineLinkSourcesIntegrationTest extends AbstractAgentIntegrationTest {

    @Autowired AudioPlayerManager audioPlayerManager;

    @Test
    void buildsNoLinkSourcesThatCallOutDuringTests() {
        // the real ones include Twitch, which fetches twitch.tv headers as soon as it is built
        assertThat(audioPlayerManager.getSourceManagers()).extracting(AudioSourceManager::getSourceName)
                .containsExactly("youtube");
    }
}
