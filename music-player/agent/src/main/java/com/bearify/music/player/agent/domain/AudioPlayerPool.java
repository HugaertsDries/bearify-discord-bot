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

@Component
public class AudioPlayerPool {

    private record GuildEntry(AudioPlayer player, AudioTrackLoader loader) {}

    private final ConcurrentHashMap<String, GuildEntry> entries = new ConcurrentHashMap<>();
    private final MusicPlayerEventDispatcher eventDispatcher;
    private final PlayerProperties properties;
    private final String playerId;

    public AudioPlayerPool(MusicPlayerEventDispatcher eventDispatcher,
                           PlayerProperties properties,
                           @Value("${player.id}") String playerId) {
        this.eventDispatcher = eventDispatcher;
        this.properties = properties;
        this.playerId = playerId;
    }

    public AudioPlayer getOrCreate(String guildId) {
        return getOrCreateEntry(guildId).player();
    }

    public AudioTrackLoader getLoader(String guildId) {
        return getOrCreateEntry(guildId).loader();
    }

    public Optional<AudioPlayer> get(String guildId) {
        return Optional.ofNullable(entries.get(guildId)).map(GuildEntry::player);
    }

    public Set<String> activeGuildIds() {
        return new HashSet<>(entries.keySet());
    }

    private GuildEntry getOrCreateEntry(String guildId) {
        return entries.computeIfAbsent(guildId, id -> {
            LavaAudioEngine engine = new LavaAudioEngine();
            AudioTrackLoader loader = engine.getLoader();
            AudioPlayer player = new AudioPlayer(
                    engine, engine, eventDispatcher, properties, playerId, id,
                    () -> this.remove(guildId));
            return new GuildEntry(player, loader);
        });
    }

    private void remove(String guildId) {
        GuildEntry entry = entries.remove(guildId);
        if (entry != null) {
            entry.player().close();
        }
    }
}
