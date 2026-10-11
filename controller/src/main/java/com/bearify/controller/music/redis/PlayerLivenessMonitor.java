package com.bearify.controller.music.redis;

import com.bearify.controller.music.domain.MusicPlayerAnnouncementConsumer;
import com.bearify.controller.music.domain.MusicPlayerAnnouncementRegistry;
import com.bearify.controller.music.domain.MusicPlayerAnnouncementRegistry.Session;
import com.bearify.discord.api.gateway.DiscordClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ends a session whose bear died without a Stopped (killed, crashed, no time to say goodbye): its player message would
 * otherwise hang forever. The bear's heartbeat keeps its assignment key alive, so a dead bear's key expires; the session
 * only ends once Discord no longer shows the bear in voice either, so the note lines up with the bear visibly leaving.
 */
class PlayerLivenessMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(PlayerLivenessMonitor.class);

    private final MusicPlayerAnnouncementRegistry registry;
    private final RedisPlayerAssignments assignments;
    private final MusicPlayerAnnouncementConsumer consumer;
    private final DiscordClient discord;
    private final TaskScheduler scheduler;
    private final LivenessProperties properties;
    private final Clock clock;
    private final Set<Session> missingOnce = ConcurrentHashMap.newKeySet();

    PlayerLivenessMonitor(MusicPlayerAnnouncementRegistry registry,
                          RedisPlayerAssignments assignments,
                          MusicPlayerAnnouncementConsumer consumer,
                          DiscordClient discord,
                          TaskScheduler scheduler,
                          LivenessProperties properties,
                          Clock clock) {
        this.registry = registry;
        this.assignments = assignments;
        this.consumer = consumer;
        this.discord = discord;
        this.scheduler = scheduler;
        this.properties = properties;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduler.scheduleWithFixedDelay(this::tick, clock.instant().plus(properties.interval()), properties.interval());
    }

    void tick() {
        Set<Session> sessions = registry.sessions();
        // sessions that ended with a Stopped in the meantime need no second look
        missingOnce.retainAll(sessions);
        for (Session session : sessions) {
            try {
                if (assignments.isAssigned(session.playerId(), session.guildId())) {
                    missingOnce.remove(session);
                } else if (!missingOnce.add(session) && !stillInVoice(session)) {
                    // missing twice in a row: a single miss can be a /player leave whose Stopped is still on its way
                    missingOnce.remove(session);
                    LOG.info("Bear {} lost its session in guild {} without stopping", session.playerId(), session.guildId());
                    consumer.lost(session.playerId(), session.guildId());
                }
            } catch (RuntimeException e) {
                LOG.warn("Failed to check whether bear {} is still in guild {}", session.playerId(), session.guildId(), e);
            }
        }
    }

    // an unknown Discord account or an unreadable guild keeps the session: a player that hangs a little longer beats ending one that still plays
    private boolean stillInVoice(Session session) {
        try {
            return assignments.botUserId(session.playerId())
                    .map(userId -> discord.guild(session.guildId()).isInVoice(userId))
                    .orElse(true);
        } catch (RuntimeException e) {
            LOG.warn("Failed to ask Discord whether bear {} is still in voice in guild {}", session.playerId(), session.guildId(), e);
            return true;
        }
    }
}
