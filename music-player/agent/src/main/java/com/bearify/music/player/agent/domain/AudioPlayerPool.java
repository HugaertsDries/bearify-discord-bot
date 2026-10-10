package com.bearify.music.player.agent.domain;

import com.bearify.music.player.agent.config.PlayerProperties;
import com.bearify.music.player.agent.lava.LavaAudioEngine;
import com.bearify.music.player.agent.port.MusicPlayerEventDispatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

@Component
public class AudioPlayerPool {

    private final ConcurrentHashMap<String, AudioPlayer> entries = new ConcurrentHashMap<>();
    private final MusicPlayerEventDispatcher eventDispatcher;
    private final PlayerProperties properties;
    private final ScheduledExecutorService scheduler;
    private final Supplier<LavaAudioEngine> engineFactory;
    private final String playerId;

    public AudioPlayerPool(MusicPlayerEventDispatcher eventDispatcher,
                           PlayerProperties properties,
                           ScheduledExecutorService scheduler,
                           Supplier<LavaAudioEngine> engineFactory,
                           @Value("${player.id}") String playerId) {
        this.eventDispatcher = eventDispatcher;
        this.properties = properties;
        this.scheduler = scheduler;
        this.engineFactory = engineFactory;
        this.playerId = playerId;
    }

    public AudioPlayer getOrCreate(String guildId) {
        return entries.computeIfAbsent(guildId, id -> {
            LavaAudioEngine engine = engineFactory.get();
            return new AudioPlayer(
                    engine, engine, eventDispatcher, properties, scheduler, playerId, id,
                    () -> this.remove(guildId));
        });
    }

    public Optional<AudioPlayer> get(String guildId) {
        return Optional.ofNullable(entries.get(guildId));
    }

    public Set<String> activeGuildIds() {
        return new HashSet<>(entries.keySet());
    }

    private void remove(String guildId) {
        AudioPlayer player = entries.remove(guildId);
        if (player != null) {
            player.close();
        }
    }
}
