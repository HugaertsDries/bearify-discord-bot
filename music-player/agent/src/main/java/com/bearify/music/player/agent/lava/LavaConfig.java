package com.bearify.music.player.agent.lava;

import com.bearify.music.player.agent.config.HealthProbeProperties;
import com.bearify.music.player.agent.config.PlayerProperties;
import com.bearify.music.player.agent.domain.AudioTrackLoader;
import com.bearify.music.player.agent.domain.HealthProbe;
import com.bearify.music.player.agent.port.MusicPlayerEventDispatcher;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.AndroidVr;
import dev.lavalink.youtube.clients.Music;
import dev.lavalink.youtube.clients.Tv;
import dev.lavalink.youtube.clients.Web;
import dev.lavalink.youtube.clients.WebEmbedded;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

@Configuration
public class LavaConfig {

    /** The sources for pasted links besides YouTube; built when the player manager is, so tests can swap them out. */
    @FunctionalInterface
    public interface LinkSources {
        List<AudioSourceManager> create();
    }

    @Bean
    LinkSources linkSources() {
        // no generic HTTP source, so the bear never fetches an arbitrary address;
        // Twitch's constructor fetches its client headers from twitch.tv once at startup
        return () -> List.of(
                SoundCloudAudioSourceManager.createDefault(),
                new BandcampAudioSourceManager(),
                new VimeoAudioSourceManager(),
                new TwitchStreamAudioSourceManager());
    }

    @Bean(destroyMethod = "shutdown")
    AudioPlayerManager audioPlayerManager(PlayerProperties properties, LinkSources linkSources) {
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
        linkSources.create().forEach(playerManager::registerSourceManager);
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

    @Bean
    HealthProbe healthProbe(AudioTrackLoader audioTrackLoader,
                            Supplier<LavaAudioEngine> lavaAudioEngineFactory,
                            MusicPlayerEventDispatcher eventDispatcher,
                            ScheduledExecutorService scheduler,
                            HealthProbeProperties properties,
                            @Value("${player.id}") String playerId) {
        return new HealthProbe(audioTrackLoader, lavaAudioEngineFactory, eventDispatcher, scheduler, properties, playerId);
    }
}
