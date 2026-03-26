package com.bearify.controller.music.discord;

import com.bearify.controller.music.discord.images.DiskGIF;
import com.bearify.controller.music.discord.images.SpacerPng;
import com.bearify.controller.music.discord.images.VibingGIF;
import com.bearify.controller.music.domain.MusicPlayerTrackAnnouncer;
import com.bearify.discord.api.gateway.DiscordClient;
import com.bearify.discord.api.gateway.EmbedMessage;
import com.bearify.discord.api.gateway.SentMessage;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.TrackMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class TextChannelMusicPlayerTrackAnnouncer implements MusicPlayerTrackAnnouncer {

    private enum PlaybackState {
        PLAYING("\uD83D\uDD34 ON THE AIR", "vibing.gif"),
        PAUSED("\u26AA ON THE AIR", "spacer.png");

        private final String authorText;
        private final String imageFilename;

        PlaybackState(String authorText, String imageFilename) {
            this.authorText = authorText;
            this.imageFilename = imageFilename;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(TextChannelMusicPlayerTrackAnnouncer.class);
    private final ScheduledExecutorService actionTimeouts =
            Executors.newSingleThreadScheduledExecutor(new AnnouncerThreadFactory());

    private static final String DISK_FILENAME  = "disk.gif";
    private static final byte[] SPACER_BYTES   = new SpacerPng().toBytes();

    private final DiscordClient discord;
    private final AnnouncerProperties properties;
    private final String textChannelId;
    private volatile SentMessage activeEmbed;
    private volatile PlaybackState playbackState = PlaybackState.PLAYING;
    private volatile String temporaryAction;
    private volatile ScheduledFuture<?> clearActionTask;
    private volatile TrackMetadata currentTrack;
    private volatile List<TrackMetadata> currentUpNext = List.of();
    private volatile String currentRequesterTag;
    private volatile byte[] diskBytes;
    private volatile byte[] vibingBytes;

    public TextChannelMusicPlayerTrackAnnouncer(DiscordClient discord,
                                                AnnouncerProperties properties,
                                                String textChannelId) {
        this.discord = discord;
        this.properties = properties;
        this.textChannelId = textChannelId;
    }

    @Override
    public synchronized void accept(MusicPlayerEvent event) {
        switch (event) {
            case MusicPlayerEvent.TrackStart started -> onTrackStart(started);
            case MusicPlayerEvent.TrackError error -> onTrackError(error);
            case MusicPlayerEvent.Paused paused -> updatePlaybackState(PlaybackState.PAUSED);
            case MusicPlayerEvent.Resumed resumed -> updatePlaybackState(PlaybackState.PLAYING);
            case MusicPlayerEvent.Skipped skipped -> notify("Last track skipped by " + skipped.request().requesterTag());
            case MusicPlayerEvent.WentBack wentBack -> notify("Jumped back by " + wentBack.request().requesterTag());
            case MusicPlayerEvent.Rewound rewound -> notify("Rewound by " + rewound.request().requesterTag());
            case MusicPlayerEvent.Forwarded forwarded -> notify("Forwarded by " + forwarded.request().requesterTag());
            case MusicPlayerEvent.Cleared cleared -> notify("Cleared by " + cleared.request().requesterTag());
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
            playbackState = PlaybackState.PLAYING;
            diskBytes = new DiskGIF().toBytes();
            vibingBytes = new VibingGIF().toBytes();
            updateOrPost(nowPlayingEmbed());
        } catch (Exception e) {
            LOG.warn("Failed to post now-playing embed for channel {}", textChannelId, e);
        }
    }

    private void onTrackError(MusicPlayerEvent.TrackError event) {
        currentTrack = event.track();
        currentRequesterTag = null;
        cancelClearTask();
        temporaryAction = null;
        try {
            updateOrPost(errorEmbed(event.track()));
        } catch (Exception e) {
            LOG.warn("Failed to post error embed for channel {}", textChannelId, e);
        }
    }

    private void onQueueUpdated(MusicPlayerEvent.QueueUpdated event) {
        currentUpNext = event.upNext();
        refreshNowPlayingEmbed();
    }

    private void updatePlaybackState(PlaybackState state) {
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
        if (currentTrack == null || activeEmbed == null) {
            return;
        }
        try {
            updateOrPost(nowPlayingEmbed());
        } catch (Exception e) {
            LOG.warn("Failed to refresh embed for channel {}", textChannelId, e);
        }
    }

    private void updateOrPost(EmbedMessage embed) {
        SentMessage existing = activeEmbed;
        if (existing != null) {
            try {
                existing.update(embed);
                return;
            } catch (Exception e) {
                LOG.warn("Failed to update embed for channel {}, will post fresh", textChannelId, e);
                activeEmbed = null;
            }
        }
        activeEmbed = discord.textChannel(textChannelId).send(embed);
    }

    private void deleteEmbed() {
        cancelClearTask();
        currentTrack = null;
        currentRequesterTag = null;
        currentUpNext = List.of();
        temporaryAction = null;
        SentMessage existing = activeEmbed;
        activeEmbed = null;
        if (existing != null) {
            try {
                existing.delete();
            } catch (Exception e) {
                LOG.warn("Failed to delete embed for channel {}", textChannelId, e);
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

    private EmbedMessage nowPlayingEmbed() {
        String requestedBy = currentRequesterTag != null ? currentRequesterTag : "Unknown";
        String description = temporaryAction != null && !temporaryAction.isBlank()
                ? "*" + temporaryAction + "*"
                : null;

        List<EmbedMessage.Field> fields = new ArrayList<>();
        fields.add(new EmbedMessage.Field("Author",       truncate(currentTrack.author(), 40), true));
        fields.add(new EmbedMessage.Field("Length",       humanReadable(Duration.ofMillis(currentTrack.durationMs())), true));
        fields.add(new EmbedMessage.Field("Requested by", requestedBy,                         true));
        if (!currentUpNext.isEmpty()) {
            fields.add(new EmbedMessage.Field("Up Next", formatUpNext(currentUpNext), false));
        }

        return EmbedMessage.builder()
                .authorText(playbackState.authorText)
                .title(truncate(currentTrack.title(), 30))
                .titleUrl(currentTrack.uri())
                .description(description)
                .color(properties.colorNowPlayingInt())
                .imageFilename(playbackState.imageFilename)
                .thumbnailFilename(DISK_FILENAME)
                .footer(properties.footer())
                .fields(fields)
                .attachments(attachments())
                .build();
    }

    private EmbedMessage errorEmbed(TrackMetadata track) {
        return EmbedMessage.builder()
                .title(track.title())
                .titleUrl(track.uri())
                .description("Something went wrong loading this track. Skipping in 5 seconds\u2026")
                .color(properties.colorErrorInt())
                .footer(properties.footer())
                .build();
    }

    private List<EmbedMessage.Attachment> attachments() {
        byte[] stateImageBytes = playbackState == PlaybackState.PLAYING ? vibingBytes : SPACER_BYTES;
        return List.of(
                new EmbedMessage.Attachment(DISK_FILENAME, diskBytes),
                new EmbedMessage.Attachment(playbackState.imageFilename, stateImageBytes)
        );
    }

    private static String formatUpNext(List<TrackMetadata> tracks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tracks.size(); i++) {
            TrackMetadata t = tracks.get(i);
            if (i > 0) sb.append('\n');
            sb.append(i + 1).append(". ")
              .append(truncate(t.title(), 35));
        }
        return sb.toString();
    }

    private static String truncate(String text, int limit) {
        if (text == null) return "";
        return text.length() <= limit ? text : text.substring(0, limit - 1) + "\u2026";
    }

    private static String humanReadable(Duration duration) {
        return duration.toString()
                .substring(2)
                .replaceAll("(\\d[HMS])(?!$)", "$1 ")
                .toLowerCase();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TextChannelMusicPlayerTrackAnnouncer that)) return false;
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
