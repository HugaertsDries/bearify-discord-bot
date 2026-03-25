package com.bearify.controller.music.discord;

import com.bearify.controller.music.discord.images.DiskGIF;
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
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class TextChannelMusicPlayerTrackAnnouncer implements MusicPlayerTrackAnnouncer {

    private static final Logger LOG = LoggerFactory.getLogger(TextChannelMusicPlayerTrackAnnouncer.class);
    private static final Random RANDOM = new Random();
    private static final List<String> FOOTER_ENDINGS = List.of(
            "Certified banger detector\u2122",
            "Press play, regret nothing",
            "This is un-bear-ably good",
            "100% bug-free (probably)",
            "Compiled with love",
            "Running on caffeine & vibes"
    );
    private static final String DISK_FILENAME = "disk.gif";
    private static final String VIBING_FILENAME = "vibing.gif";

    private final DiscordClient discord;
    private final AnnouncerProperties properties;
    private final String textChannelId;
    private volatile SentMessage activeEmbed;

    public TextChannelMusicPlayerTrackAnnouncer(DiscordClient discord,
                                                AnnouncerProperties properties,
                                                String textChannelId) {
        this.discord = discord;
        this.properties = properties;
        this.textChannelId = textChannelId;
    }

    @Override
    public void accept(MusicPlayerEvent event) {
        switch (event) {
            case MusicPlayerEvent.TrackStart started -> onTrackStart(started);
            case MusicPlayerEvent.TrackError error -> onTrackError(error);
            case MusicPlayerEvent.QueueEmpty ignored -> deleteEmbed();
            case MusicPlayerEvent.Stopped ignored -> deleteEmbed();
            default -> {
            }
        }
    }

    private void onTrackStart(MusicPlayerEvent.TrackStart event) {
        try {
            byte[] diskBytes = new DiskGIF().toBytes();
            byte[] vibingBytes = new VibingGIF().toBytes();
            updateOrPost(nowPlayingEmbed(event.track(), event.requesterTag(), diskBytes, vibingBytes));
        } catch (Exception e) {
            LOG.warn("Failed to post now-playing embed for channel {}", textChannelId, e);
        }
    }

    private void onTrackError(MusicPlayerEvent.TrackError event) {
        try {
            updateOrPost(errorEmbed(event.track()));
        } catch (Exception e) {
            LOG.warn("Failed to post error embed for channel {}", textChannelId, e);
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

    private EmbedMessage nowPlayingEmbed(TrackMetadata track, String requesterTag, byte[] diskBytes, byte[] vibingBytes) {
        String addedBy = requesterTag != null ? requesterTag : "Unknown";
        return EmbedMessage.builder()
                .authorText("LISTENING TO")
                .title(track.title())
                .titleUrl(track.uri())
                .color(properties.colorNowPlayingInt())
                .imageFilename(VIBING_FILENAME)
                .thumbnailFilename(DISK_FILENAME)
                .footer(footer())
                .fields(List.of(
                        new EmbedMessage.Field("Added by", addedBy, true),
                        new EmbedMessage.Field("Author", track.author(), true),
                        new EmbedMessage.Field("Length", humanReadable(Duration.ofMillis(track.durationMs())), true)
                ))
                .attachments(List.of(
                        new EmbedMessage.Attachment(DISK_FILENAME, diskBytes),
                        new EmbedMessage.Attachment(VIBING_FILENAME, vibingBytes)
                ))
                .build();
    }

    private EmbedMessage errorEmbed(TrackMetadata track) {
        return EmbedMessage.builder()
                .title(track.title())
                .titleUrl(track.uri())
                .description("Something went wrong loading this track. Skipping in 5 seconds\u2026")
                .color(properties.colorErrorInt())
                .footer(footer())
                .build();
    }

    private static String humanReadable(Duration duration) {
        return duration.toString()
                .substring(2)
                .replaceAll("(\\d[HMS])(?!$)", "$1 ")
                .toLowerCase();
    }

    private String footer() {
        return properties.footerMain() + " \u2022 " + FOOTER_ENDINGS.get(RANDOM.nextInt(FOOTER_ENDINGS.size()));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TextChannelMusicPlayerTrackAnnouncer that)) {
            return false;
        }
        return Objects.equals(textChannelId, that.textChannelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(textChannelId);
    }
}
