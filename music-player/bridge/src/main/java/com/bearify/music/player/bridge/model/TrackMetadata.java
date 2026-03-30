package com.bearify.music.player.bridge.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

public final class TrackMetadata {
    private final String title;
    private final String author;
    private final String uri;
    private final long durationMs;
    private final String artworkUrl;

    public TrackMetadata(String title, String author, String uri, long durationMs) {
        this(title, author, uri, durationMs, null);
    }

    @JsonCreator
    public TrackMetadata(@JsonProperty("title") String title,
                         @JsonProperty("author") String author,
                         @JsonProperty("uri") String uri,
                         @JsonProperty("durationMs") long durationMs,
                         @JsonProperty("artworkUrl") String artworkUrl) {
        this.title = title;
        this.author = author;
        this.uri = uri;
        this.durationMs = durationMs;
        this.artworkUrl = artworkUrl;
    }

    @JsonProperty("title")
    public String title() {
        return title;
    }

    @JsonProperty("author")
    public String author() {
        return author;
    }

    @JsonProperty("uri")
    public String uri() {
        return uri;
    }

    @JsonIgnore
    public URI uriValue() {
        return URI.create(uri);
    }

    @JsonProperty("durationMs")
    public long durationMs() {
        return durationMs;
    }

    @JsonIgnore
    public Optional<String> artworkUrl() {
        return Optional.ofNullable(artworkUrl);
    }

    @JsonIgnore
    public Optional<URI> artworkUri() {
        return Optional.ofNullable(artworkUrl).map(URI::create);
    }

    @JsonProperty("artworkUrl")
    public String rawArtworkUrl() {
        return artworkUrl;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TrackMetadata that)) {
            return false;
        }
        return durationMs == that.durationMs
                && Objects.equals(title, that.title)
                && Objects.equals(author, that.author)
                && Objects.equals(uri, that.uri)
                && Objects.equals(artworkUrl, that.artworkUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, author, uri, durationMs, artworkUrl);
    }

    @Override
    public String toString() {
        return "TrackMetadata[title=%s, author=%s, uri=%s, durationMs=%d, artworkUrl=%s]"
                .formatted(title, author, uri, durationMs, artworkUrl);
    }
}
