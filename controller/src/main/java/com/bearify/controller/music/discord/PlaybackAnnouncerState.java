package com.bearify.controller.music.discord;

import com.bearify.music.player.bridge.model.TrackMetadata;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public final class PlaybackAnnouncerState {

    private static final String DEFAULT_FOOTER = "Bearify \u2022 Powered by Bearable Software";

    public enum PlaybackState {
        PLAYING("\uD83D\uDD34 \u200E ON THE AIR"),
        PAUSED("\u26AA \u200E ON THE AIR");

        private final String header;

        PlaybackState(String header) {
            this.header = header;
        }

        public String header() {
            return header;
        }
    }

    private final PlaybackState playbackState;
    private final String notification;
    private final TrackMetadata track;
    private final String requesterTag;
    private final List<TrackMetadata> upNext;
    private final URI artworkUri;
    private final String footer;
    private final boolean paused;

    private PlaybackAnnouncerState(Builder builder) {
        this.playbackState = builder.playbackState;
        this.notification = builder.notification;
        this.track = builder.track;
        this.requesterTag = builder.requesterTag;
        this.upNext = List.copyOf(builder.upNext);
        this.artworkUri = builder.artworkUri;
        this.footer = builder.footerText;
        this.paused = builder.paused;
    }

    public static Builder builder() {
        return new Builder();
    }

    public PlaybackState playbackState() {
        return playbackState;
    }

    public Optional<String> notification() {
        return Optional.ofNullable(notification);
    }

    public TrackMetadata track() {
        return track;
    }

    public String requesterTag() {
        return requesterTag;
    }

    public List<TrackMetadata> upNext() {
        return upNext;
    }

    public Optional<URI> artworkUri() {
        return Optional.ofNullable(artworkUri);
    }

    public String footerText() {
        return footer;
    }

    public boolean paused() {
        return paused;
    }

    public static final class Builder {
        private PlaybackState playbackState = PlaybackState.PLAYING;
        private String notification;
        private TrackMetadata track;
        private String requesterTag;
        private List<TrackMetadata> upNext = List.of();
        private URI artworkUri;
        private String footerText = DEFAULT_FOOTER;
        private boolean paused;

        private Builder() {
        }

        public Builder playbackState(PlaybackState playbackState) {
            this.playbackState = playbackState == null ? PlaybackState.PLAYING : playbackState;
            return this;
        }

        public Builder notification(String notification) {
            this.notification = notification;
            return this;
        }

        public Builder track(TrackMetadata track) {
            this.track = track;
            return this;
        }

        public Builder requesterTag(String requesterTag) {
            this.requesterTag = requesterTag;
            return this;
        }

        public Builder upNext(List<TrackMetadata> upNext) {
            this.upNext = upNext == null ? List.of() : List.copyOf(upNext);
            return this;
        }

        public Builder artworkUri(URI artworkUri) {
            this.artworkUri = artworkUri;
            return this;
        }

        public Builder footer(String footerText) {
            this.footerText = footerText == null ? DEFAULT_FOOTER : footerText;
            return this;
        }

        public Builder paused(boolean paused) {
            this.paused = paused;
            return this;
        }

        public PlaybackAnnouncerState build() {
            return new PlaybackAnnouncerState(this);
        }
    }
}
