package com.bearify.controller.music.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.controller.music.domain.MusicPlayerEventConsumer;
import com.bearify.controller.music.domain.PlayerMessageStore;
import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.SentMessage;
import com.bearify.discord.api.message.ComponentMessage;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.LoopMode;
import com.bearify.music.player.bridge.model.TrackMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class DiscordPlaybackAnnouncer implements MusicPlayerEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DiscordPlaybackAnnouncer.class);
    static final String NOBODY_LISTENING_NOTICE = "Paused: it's lonely in here";
    static final String NOBODY_LISTENING_LEAVE_NOTE = BearifyEmoji.BEAR + " | Nobody was listening, so I wandered back to my cave.";
    static final String IDLE_LEAVE_NOTE = BearifyEmoji.BEAR + " | Nothing left to play, so I went for a nap.";
    static final String DISCONNECTED_LEAVE_NOTE = BearifyEmoji.BEAR + " | I got disconnected from the voice channel, so I packed up.";
    static final String LOST_LEAVE_NOTE = BearifyEmoji.BEAR + " | I lost my connection and had to leave the voice channel.";
    static final String LIVE_STREAM_FAILED_NOTE = BearifyEmoji.BLOCKED + " | I couldn't tune in to that live stream.";
    static final String TRACK_FAILED_NOTE = BearifyEmoji.BLOCKED + " | I couldn't play that track.";
    // Shared by all announcers: one daemon thread instead of one per announcer that is never shut down
    private static final ScheduledExecutorService ACTION_TIMEOUTS =
            Executors.newSingleThreadScheduledExecutor(new AnnouncerThreadFactory());

    private final DiscordClient discord;
    private final AnnouncerProperties properties;
    private final PlayerMessageStore store;
    private final String playerId;
    private final String guildId;
    private final PlaybackComponent playbackComponent;
    private final YoutubeThumbnailResolver artworkUrlResolver = new YoutubeThumbnailResolver();
    private final String textChannelId;
    private volatile SentMessage activeMessage;
    private volatile PlaybackComponentState.PlaybackState playbackState = PlaybackComponentState.PlaybackState.PLAYING;
    private volatile String temporaryAction;
    private volatile boolean autoPaused;
    // a failed last track would otherwise vanish with the player message, so the queue's end says what happened
    private volatile Optional<String> failedNote = Optional.empty();
    private volatile ScheduledFuture<?> clearActionTask;
    private long labelVersion;
    private volatile TrackMetadata currentTrack;
    private volatile List<TrackMetadata> currentUpNext = List.of();
    private volatile int currentQueueSize;
    private volatile Duration currentQueueDuration = Duration.ZERO;
    private volatile String currentRequesterTag;
    private volatile int currentVolume = 100;
    private volatile LoopMode currentLoopMode = LoopMode.OFF;
    private volatile boolean currentShuffle;

    public DiscordPlaybackAnnouncer(DiscordClient discord,
                                    AnnouncerProperties properties,
                                    PlayerMessageStore store,
                                    String playerId,
                                    String guildId,
                                    String textChannelId) {
        this.discord = discord;
        this.properties = properties;
        this.store = store;
        this.playerId = playerId;
        this.guildId = guildId;
        this.playbackComponent = new PlaybackComponent(properties);
        this.textChannelId = textChannelId;
    }

    @Override
    public synchronized void accept(MusicPlayerEvent event) {
        switch (event) {
            case MusicPlayerEvent.TrackStart started -> onTrackStart(started);
            case MusicPlayerEvent.TrackError error -> onTrackError(error);
            case MusicPlayerEvent.Paused paused -> updatePlaybackState(PlaybackComponentState.PlaybackState.PAUSED, false);
            case MusicPlayerEvent.Resumed resumed -> updatePlaybackState(PlaybackComponentState.PlaybackState.PLAYING, false);
            case MusicPlayerEvent.AutoPaused ignored -> updatePlaybackState(PlaybackComponentState.PlaybackState.PAUSED, true);
            case MusicPlayerEvent.AutoResumed autoResumed -> updatePlaybackState(PlaybackComponentState.PlaybackState.PLAYING, false);
            case MusicPlayerEvent.Skipped skipped -> notify(skipped.request().requesterTag() + " skipped the track");
            case MusicPlayerEvent.WentBack wentBack -> notify(wentBack.request().requesterTag() + " went back a track");
            case MusicPlayerEvent.Restarted restarted -> notify(restarted.request().requesterTag() + " restarted the track");
            case MusicPlayerEvent.Rewound rewound -> notify(rewound.request().requesterTag() + " rewound " + PlaybackComponent.humanReadable(Duration.ofMillis(rewound.seekMs())));
            case MusicPlayerEvent.Forwarded forwarded -> notify(forwarded.request().requesterTag() + " skipped ahead " + PlaybackComponent.humanReadable(Duration.ofMillis(forwarded.seekMs())));
            case MusicPlayerEvent.Jumped jumped -> notify(jumped.request().requesterTag() + " jumped to " + PlaybackComponent.humanReadable(Duration.ofMillis(jumped.positionMs())));
            case MusicPlayerEvent.Cleared cleared -> onCleared(cleared);
            case MusicPlayerEvent.Removed removed -> onRemoved(removed);
            case MusicPlayerEvent.ShuffleChanged shuffleChanged -> onShuffleChanged(shuffleChanged);
            case MusicPlayerEvent.VolumeChanged volumeChanged -> onVolumeChanged(volumeChanged);
            case MusicPlayerEvent.LoopModeChanged loopModeChanged -> onLoopModeChanged(loopModeChanged);
            case MusicPlayerEvent.QueueUpdated queueUpdated -> onQueueUpdated(queueUpdated);
            case MusicPlayerEvent.QueueEmpty ignored -> onQueueEmpty();
            case MusicPlayerEvent.Stopped stopped -> onStopped(stopped);
            default -> {
            }
        }
    }

    private void onTrackStart(MusicPlayerEvent.TrackStart event) {
        cancelClearTask();
        temporaryAction = null;
        autoPaused = false;
        failedNote = Optional.empty();
        try {
            currentTrack = event.track();
            currentUpNext = event.upNext();
            currentQueueSize = event.queueSize();
            currentQueueDuration = Duration.ofMillis(event.queueDurationMs());
            currentRequesterTag = event.request().requesterTag();
            currentVolume = event.volume();
            currentLoopMode = event.loopMode();
            currentShuffle = event.shuffle();
            playbackState = PlaybackComponentState.PlaybackState.PLAYING;
            updateOrPost(playbackComponent.render(currentState()));
        } catch (Exception e) {
            LOG.warn("Failed to post now-playing component message for channel {}", textChannelId, e);
        }
    }

    private void onTrackError(MusicPlayerEvent.TrackError event) {
        failedNote = Optional.of(event.track().isLive() ? LIVE_STREAM_FAILED_NOTE : TRACK_FAILED_NOTE);
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
        currentQueueSize = event.queueSize();
        currentQueueDuration = Duration.ofMillis(event.queueDurationMs());
        refreshNowPlayingEmbed();
    }

    private void onCleared(MusicPlayerEvent.Cleared event) {
        currentUpNext = event.upNext();
        currentQueueSize = event.upNext().size();
        currentQueueDuration = Duration.ZERO;
        notify(event.request().requesterTag() + " cleared the queue");
    }

    private void onRemoved(MusicPlayerEvent.Removed event) {
        currentUpNext = event.upNext();
        currentQueueSize = event.queueSize();
        currentQueueDuration = Duration.ofMillis(event.queueDurationMs());
        notify(event.request().requesterTag() + " removed **" + PlaybackComponent.escaped(PlaybackComponent.truncate(event.track().title(), 50)) + "**");
    }

    private void onShuffleChanged(MusicPlayerEvent.ShuffleChanged event) {
        currentShuffle = event.shuffle();
        currentUpNext = event.upNext();
        currentQueueSize = event.queueSize();
        // same tracks in a new order, so the queue's duration stays as it was
        notify(event.request().requesterTag() + " " + shuffleAction(event.shuffle()));
    }

    /** What someone did to shuffle, said after their name. */
    static String shuffleAction(boolean shuffle) {
        return shuffle ? "turned shuffle on" : "turned shuffle off";
    }

    private void onVolumeChanged(MusicPlayerEvent.VolumeChanged event) {
        currentVolume = event.volume();
        notify(event.request().requesterTag() + " set the volume to " + event.volume() + "%");
    }

    private void onLoopModeChanged(MusicPlayerEvent.LoopModeChanged event) {
        currentLoopMode = event.mode();
        notify(event.request().requesterTag() + " " + loopAction(event.mode()));
    }

    /** What someone did to the loop, said after their name. */
    static String loopAction(LoopMode mode) {
        return switch (mode) {
            case OFF -> "turned looping off";
            case QUEUE -> "looped the queue";
            case TRACK -> "put this track on repeat";
        };
    }

    private void updatePlaybackState(PlaybackComponentState.PlaybackState state, boolean autoPaused) {
        playbackState = state;
        this.autoPaused = autoPaused;
        refreshNowPlayingEmbed();
    }

    private void onStopped(MusicPlayerEvent.Stopped event) {
        deleteEmbed();
        Optional<String> note = switch (event.reason()) {
            case NOBODY_LISTENING -> Optional.of(NOBODY_LISTENING_LEAVE_NOTE);
            case IDLE -> Optional.of(IDLE_LEAVE_NOTE);
            case DISCONNECTED -> Optional.of(DISCONNECTED_LEAVE_NOTE);
            case LOST -> Optional.of(LOST_LEAVE_NOTE);
            case REQUESTED -> Optional.empty();
        };
        note.ifPresent(this::postNote);
    }

    private void onQueueEmpty() {
        deleteEmbed();
        failedNote.ifPresent(this::postNote);
        failedNote = Optional.empty();
    }

    private void postNote(String text) {
        try {
            discord.textChannel(textChannelId).send(text);
        } catch (Exception e) {
            LOG.warn("Failed to post note for channel {}", textChannelId, e);
        }
    }

    private void notify(String action) {
        temporaryAction = action;
        cancelClearTask();
        long version = labelVersion;
        // Hand the blocking Discord edit off so one slow guild can't delay every other guild's clear
        clearActionTask = ACTION_TIMEOUTS.schedule(
                () -> Thread.startVirtualThread(() -> clearTemporaryActionSafely(version)),
                properties.actionTimeout().toMillis(), TimeUnit.MILLISECONDS);
        refreshNowPlayingEmbed();
    }

    private void clearTemporaryActionSafely(long version) {
        synchronized (this) {
            // A clear only removes the label version it was scheduled for; a cancel can't stop one already waiting for this lock
            if (version != labelVersion) {
                return;
            }
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
        SentMessage posted = discord.textChannel(textChannelId).send(message);
        activeMessage = posted;
        // remembered so a restarted controller, or the liveness check, can still delete it
        try {
            store.remember(playerId, guildId, textChannelId, posted.id());
        } catch (Exception e) {
            LOG.warn("Failed to remember component message for channel {}", textChannelId, e);
        }
    }

    private void deleteEmbed() {
        cancelClearTask();
        currentTrack = null;
        currentRequesterTag = null;
        currentUpNext = List.of();
        currentQueueSize = 0;
        currentQueueDuration = Duration.ZERO;
        temporaryAction = null;
        autoPaused = false;
        SentMessage existing = activeMessage;
        activeMessage = null;
        if (existing != null) {
            try {
                existing.delete();
            } catch (Exception e) {
                LOG.warn("Failed to delete component message for channel {}", textChannelId, e);
            }
            try {
                store.forget(playerId, guildId, textChannelId, existing.id());
            } catch (Exception e) {
                LOG.warn("Failed to forget component message for channel {}", textChannelId, e);
            }
        }
    }

    private void cancelClearTask() {
        labelVersion++;
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
                .queueSize(currentQueueSize)
                .queueDuration(currentQueueDuration)
                .volume(currentVolume)
                .loopMode(currentLoopMode)
                .shuffle(currentShuffle)
                .artworkUri(artworkUrlResolver.resolve(currentTrack).orElse(null))
                .footer(properties.footer())
                .paused(playbackState == PlaybackComponentState.PlaybackState.PAUSED)
                .build();
    }

    private Optional<PlaybackComponentState.Notification> currentNotification() {
        return Optional.ofNullable(temporaryAction)
                .filter(text -> !text.isBlank())
                .or(() -> autoPaused ? Optional.of(NOBODY_LISTENING_NOTICE) : Optional.empty())
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
