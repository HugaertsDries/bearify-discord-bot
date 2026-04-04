package com.bearify.controller.music.discord;

import com.bearify.controller.music.domain.PlaybackAnnouncer;
import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.SentMessage;
import com.bearify.discord.api.message.ComponentMessage;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.TrackMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class DiscordPlaybackAnnouncer implements PlaybackAnnouncer {

    private static final Logger LOG = LoggerFactory.getLogger(DiscordPlaybackAnnouncer.class);
    private final ScheduledExecutorService actionTimeouts =
            Executors.newSingleThreadScheduledExecutor(new AnnouncerThreadFactory());

    private final DiscordClient discord;
    private final AnnouncerProperties properties;
    private final PlaybackComponent playbackComponent;
    private final YoutubeThumbnailResolver artworkUrlResolver = new YoutubeThumbnailResolver();
    private final String textChannelId;
    private volatile SentMessage activeMessage;
    private volatile PlaybackComponentState.PlaybackState playbackState = PlaybackComponentState.PlaybackState.PLAYING;
    private volatile String temporaryAction;
    private volatile ScheduledFuture<?> clearActionTask;
    private volatile TrackMetadata currentTrack;
    private volatile List<TrackMetadata> currentUpNext = List.of();
    private volatile String currentRequesterTag;

    public DiscordPlaybackAnnouncer(DiscordClient discord,
                                    AnnouncerProperties properties,
                                    String textChannelId) {
        this.discord = discord;
        this.properties = properties;
        this.playbackComponent = new PlaybackComponent(properties);
        this.textChannelId = textChannelId;
    }

    @Override
    public synchronized void accept(MusicPlayerEvent event) {
        switch (event) {
            case MusicPlayerEvent.TrackStart started -> onTrackStart(started);
            case MusicPlayerEvent.TrackError error -> onTrackError(error);
            case MusicPlayerEvent.Paused paused -> updatePlaybackState(PlaybackComponentState.PlaybackState.PAUSED);
            case MusicPlayerEvent.Resumed resumed -> updatePlaybackState(PlaybackComponentState.PlaybackState.PLAYING);
            case MusicPlayerEvent.Skipped skipped -> notify("Skipped by " + skipped.request().requesterTag());
            case MusicPlayerEvent.WentBack wentBack -> notify("Jumped back by " + wentBack.request().requesterTag());
            case MusicPlayerEvent.Rewound rewound -> notify("Rewound by " + rewound.request().requesterTag());
            case MusicPlayerEvent.Forwarded forwarded -> notify("Forwarded by " + forwarded.request().requesterTag());
            case MusicPlayerEvent.Cleared cleared -> onCleared(cleared);
            case MusicPlayerEvent.QueueUpdated queueUpdated -> onQueueUpdated(queueUpdated);
            case MusicPlayerEvent.QueueEmpty ignored -> deleteEmbed();
            case MusicPlayerEvent.Stopped ignored -> deleteEmbed();
            default -> {
            }
        }
    }

    private void onTrackStart(MusicPlayerEvent.TrackStart event) {
        cancelClearTask();
        temporaryAction = null;
        try {
            currentTrack = event.track();
            currentUpNext = event.upNext();
            currentRequesterTag = event.request().requesterTag();
            playbackState = PlaybackComponentState.PlaybackState.PLAYING;
            updateOrPost(playbackComponent.render(currentState()));
        } catch (Exception e) {
            LOG.warn("Failed to post now-playing component message for channel {}", textChannelId, e);
        }
    }

    private void onTrackError(MusicPlayerEvent.TrackError event) {
        currentTrack = event.track();
        currentRequesterTag = null;
        cancelClearTask();
        temporaryAction = "Something went wrong loading this track. Skipping in 5 seconds…";
        try {
            updateOrPost(playbackComponent.render(currentState()));
        } catch (Exception e) {
            LOG.warn("Failed to post error component message for channel {}", textChannelId, e);
        }
    }

    private void onQueueUpdated(MusicPlayerEvent.QueueUpdated event) {
        currentUpNext = event.upNext();
        refreshNowPlayingEmbed();
    }

    private void onCleared(MusicPlayerEvent.Cleared event) {
        currentUpNext = event.upNext();
        notify("Queue cleared by " + event.request().requesterTag());
    }

    private void updatePlaybackState(PlaybackComponentState.PlaybackState state) {
        playbackState = state;
        refreshNowPlayingEmbed();
    }

    private void notify(String action) {
        temporaryAction = action;
        cancelClearTask();
        clearActionTask = actionTimeouts.schedule(this::clearTemporaryActionSafely,
                properties.actionTimeout().toMillis(), TimeUnit.MILLISECONDS);
        refreshNowPlayingEmbed();
    }

    private void clearTemporaryActionSafely() {
        synchronized (this) {
            clearActionTask = null;
            temporaryAction = null;
            refreshNowPlayingEmbed();
        }
    }

    private void refreshNowPlayingEmbed() {
        if (currentTrack == null || activeMessage == null) {
            return;
        }
        try {
            updateOrPost(playbackComponent.render(currentState()));
        } catch (Exception e) {
            LOG.warn("Failed to refresh component message for channel {}", textChannelId, e);
        }
    }

    private void updateOrPost(ComponentMessage message) {
        SentMessage existing = activeMessage;
        if (existing != null) {
            try {
                existing.update(message);
                return;
            } catch (Exception e) {
                LOG.warn("Failed to update component message for channel {}, will post fresh", textChannelId, e);
                activeMessage = null;
            }
        }
        activeMessage = discord.textChannel(textChannelId).send(message);
    }

    private void deleteEmbed() {
        cancelClearTask();
        currentTrack = null;
        currentRequesterTag = null;
        currentUpNext = List.of();
        temporaryAction = null;
        SentMessage existing = activeMessage;
        activeMessage = null;
        if (existing != null) {
            try {
                existing.delete();
            } catch (Exception e) {
                LOG.warn("Failed to delete component message for channel {}", textChannelId, e);
            }
        }
    }

    private void cancelClearTask() {
        ScheduledFuture<?> existingTask = clearActionTask;
        clearActionTask = null;
        if (existingTask != null) {
            existingTask.cancel(false);
        }
    }

    private PlaybackComponentState currentState() {
        return PlaybackComponentState.builder()
                .playbackState(playbackState)
                .notification(currentNotification().orElse(null))
                .track(currentTrack)
                .requesterTag(currentRequesterTag != null ? currentRequesterTag : "Unknown")
                .upNext(currentUpNext)
                .artworkUri(artworkUrlResolver.resolve(currentTrack).orElse(null))
                .footer(properties.footer())
                .paused(playbackState == PlaybackComponentState.PlaybackState.PAUSED)
                .build();
    }

    private Optional<PlaybackComponentState.Notification> currentNotification() {
        return Optional.ofNullable(temporaryAction)
                .filter(text -> !text.isBlank())
                .map(text -> new PlaybackComponentState.Notification(notificationStyleFor(text), text));
    }

    private PlaybackComponentState.NotificationStyle notificationStyleFor(String text) {
        return text.startsWith("Something went wrong")
                ? PlaybackComponentState.NotificationStyle.ERROR
                : PlaybackComponentState.NotificationStyle.INFO;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiscordPlaybackAnnouncer that)) return false;
        return Objects.equals(textChannelId, that.textChannelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(textChannelId);
    }

    private static final class AnnouncerThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "bearify-announcer-timeouts");
            thread.setDaemon(true);
            return thread;
        }
    }
}
