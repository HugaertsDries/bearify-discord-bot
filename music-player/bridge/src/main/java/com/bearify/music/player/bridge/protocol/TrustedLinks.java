package com.bearify.music.player.bridge.protocol;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Which queries the bear will load: a search ({@code ytsearch:…}) or an http(s) link to a host one of its sources
 * serves. The sources' own link patterns are loose (Bandcamp's accepts {@code http://redis:6379/#.bandcamp.com/track/x}),
 * so without this a pasted link could make the bear fetch an internal address. Shared with the controller, which picks
 * the playable links out of a chat message by the same rule.
 */
public final class TrustedLinks {

    // shortcut: the hosts the sources in the agent's LavaConfig serve, subdomains included; add one here when adding a source
    private static final List<String> TRUSTED_HOSTS = List.of("youtube.com", "youtu.be", "soundcloud.com", "bandcamp.com", "vimeo.com", "twitch.tv");
    // anything that starts like scheme://, as opposed to a search such as ytsearch:
    private static final Pattern LINK = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*://");

    private TrustedLinks() {}

    public static boolean isTrusted(String query) {
        if (!LINK.matcher(query).lookingAt()) {
            return true;
        }
        try {
            URI uri = new URI(query);
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https"))
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null || uri.getHost() == null) {
                return false;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return TRUSTED_HOSTS.stream().anyMatch(trusted -> host.equals(trusted) || host.endsWith("." + trusted));
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
