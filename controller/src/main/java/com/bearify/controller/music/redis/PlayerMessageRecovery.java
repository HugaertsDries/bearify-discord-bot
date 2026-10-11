package com.bearify.controller.music.redis;

import com.bearify.controller.music.discord.DiscordPlaybackAnnouncerFactory;
import com.bearify.controller.music.domain.MusicPlayerAnnouncementRegistry;
import com.bearify.controller.music.domain.PlayerMessageStore;
import com.bearify.controller.music.domain.PlayerMessageStore.PlayerMessage;
import com.bearify.discord.api.gateway.DiscordClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.util.List;

/**
 * After a controller restart nobody holds the old player messages any more: delete them, and let each bear that is still
 * playing post a fresh one at its next track.
 */
class PlayerMessageRecovery {

    // read while beans are built, before Discord and the event subscription start, so only the last run's messages are in it
    private final List<PlayerMessage> leftBehind;

    private static final Logger LOG = LoggerFactory.getLogger(PlayerMessageRecovery.class);

    private final PlayerMessageStore store;
    private final RedisPlayerAssignments assignments;
    private final MusicPlayerAnnouncementRegistry registry;
    private final DiscordPlaybackAnnouncerFactory announcerFactory;
    private final DiscordClient discord;

    PlayerMessageRecovery(PlayerMessageStore store,
                          RedisPlayerAssignments assignments,
                          MusicPlayerAnnouncementRegistry registry,
                          DiscordPlaybackAnnouncerFactory announcerFactory,
                          DiscordClient discord) {
        this.store = store;
        this.assignments = assignments;
        this.registry = registry;
        this.announcerFactory = announcerFactory;
        this.discord = discord;
        this.leftBehind = readLeftBehind(store);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        for (PlayerMessage message : leftBehind) {
            try {
                // forgotten first, so a channel Discord no longer gives us isn't retried on every restart
                store.forget(message.playerId(), message.guildId(), message.textChannelId(), message.messageId());
                discord.textChannel(message.textChannelId()).delete(message.messageId());
            } catch (RuntimeException e) {
                LOG.warn("Failed to clean up player message {} in channel {}", message.messageId(), message.textChannelId(), e);
            }
            try {
                if (assignments.isAssigned(message.playerId(), message.guildId())) {
                    registry.subscribe(message.playerId(), message.guildId(),
                            announcerFactory.create(message.playerId(), message.guildId(), message.textChannelId()));
                }
            } catch (RuntimeException e) {
                LOG.warn("Failed to reattach the announcer for bear {} in guild {}", message.playerId(), message.guildId(), e);
            }
        }
    }

    private static List<PlayerMessage> readLeftBehind(PlayerMessageStore store) {
        try {
            return store.all();
        } catch (RuntimeException e) {
            LOG.warn("Failed to read the player messages left behind by the last run", e);
            return List.of();
        }
    }
}
