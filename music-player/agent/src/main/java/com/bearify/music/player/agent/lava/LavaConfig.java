package com.bearify.music.player.agent.lava;

import com.bearify.music.player.agent.config.PlayerProperties;
import com.bearify.music.player.agent.domain.AudioTrackLoader;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.AndroidVr;
import dev.lavalink.youtube.clients.Music;
import dev.lavalink.youtube.clients.Tv;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.WebEmbedded;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Supplier;

@Configuration
public class LavaConfig {

    @Bean(destroyMethod = "shutdown")
    AudioPlayerManager audioPlayerManager(PlayerProperties properties) {
        PlayerProperties.Engine.Youtube youtube = properties.engine().youtube();
        var playerManager = new DefaultAudioPlayerManager();
        // local signature deciphering breaks whenever YouTube changes its player script, a remote cipher server keeps up
        var options = new YoutubeSourceOptions();
        youtube.remoteCipherUrl().ifPresent(url -> options.setRemoteCipher(url, youtube.remoteCipherPassword().orElse(null), null));
        // Tv is the only OAuth-capable client; it's the fallback for videos that require login
        var source = new YoutubeAudioSourceManager(options, new Music(), new AndroidVr(), new Web(), new WebEmbedded(), new Tv());
        // without a refresh token OAuth stays off, so no device-flow login prompt
        youtube.refreshToken().ifPresent(token -> source.useOauth2(token, true));
        playerManager.registerSourceManager(source);
        return playerManager;
    }

    @Bean
    AudioTrackLoader audioTrackLoader(AudioPlayerManager audioPlayerManager, PlayerProperties properties) {
        return new LavaAudioTrackLoader(audioPlayerManager, properties.playlistMaxTracks());
    }

    @Bean
    Supplier<LavaAudioEngine> lavaAudioEngineFactory(AudioPlayerManager audioPlayerManager) {
        return () -> new LavaAudioEngine(audioPlayerManager);
    }
}
