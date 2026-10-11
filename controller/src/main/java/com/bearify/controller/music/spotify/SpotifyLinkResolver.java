package com.bearify.controller.music.spotify;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a Spotify song link into an "artist title" search, read from the public page's Open Graph tags,
 * because the official API needs a Premium developer account.
 */
@Component
public class SpotifyLinkResolver {

    private static final Pattern META = Pattern.compile("<meta\\b[^>]*>");
    private static final Pattern OG_PROPERTY = Pattern.compile("\\bproperty=\"og:([^\"]+)\"");
    private static final Pattern CONTENT = Pattern.compile("\\bcontent=\"([^\"]*)\"");
    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|amp|quot|lt|gt);");
    private static final Map<String, String> NAMED_ENTITIES = Map.of("amp", "&", "quot", "\"", "lt", "<", "gt", ">");
    private static final String SONG_TYPE = "music.song";
    // e.g. "Rick Astley · Whenever You Need Somebody · Song · 1987"
    private static final String DESCRIPTION_SEPARATOR = " · ";

    private final SpotifyPageFetcher fetcher;

    public SpotifyLinkResolver(SpotifyPageFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public Resolution resolve(String query) {
        if (!query.startsWith("https://open.spotify.com/") && !query.startsWith("https://spotify.link/")) {
            return new NotSpotify();
        }
        URI uri;
        try {
            uri = URI.create(query);
        } catch (IllegalArgumentException e) {
            return new Unreadable();
        }
        return fetcher.fetch(uri).map(SpotifyLinkResolver::fromPage).orElseGet(Unreadable::new);
    }

    private static Resolution fromPage(String html) {
        Map<String, String> og = ogTags(html);
        Optional<String> title = Optional.ofNullable(og.get("title")).filter(t -> !t.isBlank());
        if (title.isEmpty()) {
            return new Unreadable();
        }
        if (!SONG_TYPE.equals(og.get("type"))) {
            return new NotASong();
        }
        return new Resolved(Optional.ofNullable(og.get("description"))
                .filter(description -> description.contains(DESCRIPTION_SEPARATOR))
                .map(description -> description.substring(0, description.indexOf(DESCRIPTION_SEPARATOR)).strip())
                .filter(artist -> !artist.isEmpty())
                .map(artist -> artist + " " + title.get())
                .orElse(title.get()));
    }

    // attribute order differs between pages, so each <meta> tag is read on its own
    private static Map<String, String> ogTags(String html) {
        Map<String, String> tags = new HashMap<>();
        Matcher meta = META.matcher(html);
        while (meta.find()) {
            Matcher property = OG_PROPERTY.matcher(meta.group());
            Matcher content = CONTENT.matcher(meta.group());
            if (property.find() && content.find()) {
                tags.putIfAbsent(property.group(1), decode(content.group(1)));
            }
        }
        return tags;
    }

    private static String decode(String text) {
        return ENTITY.matcher(text).replaceAll(match -> Matcher.quoteReplacement(decodeEntity(match.group(1))));
    }

    private static String decodeEntity(String entity) {
        try {
            if (entity.startsWith("#x")) {
                return Character.toString(Integer.parseInt(entity.substring(2), 16));
            }
            if (entity.startsWith("#")) {
                return Character.toString(Integer.parseInt(entity.substring(1)));
            }
            return NAMED_ENTITIES.get(entity);
        } catch (IllegalArgumentException e) {
            // an out-of-range code point stays as written rather than failing the play
            return "&" + entity + ";";
        }
    }

    public sealed interface Resolution permits NotSpotify, Resolved, NotASong, Unreadable {}

    /** Not a Spotify link: the query passes through unchanged. */
    public record NotSpotify() implements Resolution {}

    public record Resolved(String searchQuery) implements Resolution {}

    /** An album, playlist, artist, episode or profile page. */
    public record NotASong() implements Resolution {}

    /** The page couldn't be fetched or carries no title. */
    public record Unreadable() implements Resolution {}
}
