package com.bearify.discord.api.gateway;

import java.util.List;
import java.util.Optional;

/**
 * Describes a Discord embed message with optional file attachments.
 * Pass to {@link TextChannel#send(EmbedMessage)} to post it.
 * Build via {@link #builder()}.
 */
public final class EmbedMessage {

    private final String title;
    private final String titleUrl;
    private final int color;
    private final String footer;
    private final String authorText;
    private final String description;
    private final String imageFilename;
    private final String thumbnailFilename;
    private final List<Field> fields;
    private final List<Attachment> attachments;

    private EmbedMessage(Builder b) {
        this.title             = b.title;
        this.titleUrl          = b.titleUrl;
        this.color             = b.color;
        this.footer            = b.footer;
        this.authorText        = b.authorText;
        this.description       = b.description;
        this.imageFilename     = b.imageFilename;
        this.thumbnailFilename = b.thumbnailFilename;
        this.fields            = List.copyOf(b.fields);
        this.attachments       = List.copyOf(b.attachments);
    }

    public String title()                         { return title; }
    public String titleUrl()                      { return titleUrl; }
    public int color()                            { return color; }
    public String footer()                        { return footer; }
    public Optional<String> authorText()          { return Optional.ofNullable(authorText); }
    public Optional<String> description()         { return Optional.ofNullable(description); }
    public Optional<String> imageFilename()       { return Optional.ofNullable(imageFilename); }
    public Optional<String> thumbnailFilename()   { return Optional.ofNullable(thumbnailFilename); }
    public List<Field> fields()                   { return fields; }
    public List<Attachment> attachments()         { return attachments; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String title;
        private String titleUrl;
        private int color;
        private String footer;
        private String authorText;
        private String description;
        private String imageFilename;
        private String thumbnailFilename;
        private List<Field> fields      = List.of();
        private List<Attachment> attachments = List.of();

        public Builder title(String title)                         { this.title = title;                         return this; }
        public Builder titleUrl(String titleUrl)                   { this.titleUrl = titleUrl;                   return this; }
        public Builder color(int color)                            { this.color = color;                         return this; }
        public Builder footer(String footer)                       { this.footer = footer;                       return this; }
        public Builder authorText(String authorText)               { this.authorText = authorText;               return this; }
        public Builder description(String description)             { this.description = description;             return this; }
        public Builder imageFilename(String imageFilename)         { this.imageFilename = imageFilename;         return this; }
        public Builder thumbnailFilename(String thumbnailFilename) { this.thumbnailFilename = thumbnailFilename; return this; }
        public Builder fields(List<Field> fields)                  { this.fields = fields;                       return this; }
        public Builder attachments(List<Attachment> attachments)   { this.attachments = attachments;             return this; }

        public EmbedMessage build() { return new EmbedMessage(this); }
    }

    public record Field(String name, String value, boolean inline) {}

    public record Attachment(String filename, byte[] data) {}
}
