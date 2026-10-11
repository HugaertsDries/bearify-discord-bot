package com.bearify.music.player.agent.lava;

import com.bearify.music.player.agent.domain.AudioTrackLoader;
import com.bearify.music.player.agent.domain.Track;
import com.bearify.music.player.bridge.model.TrackMetadata;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Adapts LavaPlayer's {@link AudioPlayerManager#loadItem} to the domain {@link AudioTrackLoader}.
 *
 * <p>Routing:
 * <ul>
 *   <li>Single track → {@code callback.trackLoaded}</li>
 *   <li>Search result (isSearchResult) → {@code callback.trackLoaded} with the first result</li>
 *   <li>Real playlist → {@code callback.playlistLoaded} with all tracks from the selected track
 *       to end, capped at {@code maxTracks}</li>
 * </ul>
 */
class LavaAudioTrackLoader implements AudioTrackLoader {

    // shortcut: the hosts the sources in LavaConfig serve, subdomains included; add one here when adding a source
    private static final List<String> TRUSTED_HOSTS = List.of("youtube.com", "youtu.be", "soundcloud.com", "bandcamp.com", "vimeo.com", "twitch.tv");
    // anything that starts like scheme://, as opposed to a search such as ytsearch:
    private static final Pattern LINK = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*://");

    private final AudioPlayerManager playerManager;
    private final int maxTracks;

    LavaAudioTrackLoader(AudioPlayerManager playerManager, int maxTracks) {
        this.playerManager = playerManager;
        this.maxTracks = maxTracks;
    }

    @Override
    public void load(String query, String requesterTag, AudioTrackLoadCallback callback) {
        if (!isTrusted(query)) {
            callback.noMatches();
            return;
        }
        playerManager.loadItem(query, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                track.setUserData(requesterTag);
                callback.trackLoaded(new LavaTrack(track, requesterTag));
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.isSearchResult()) {
                    AudioTrack first = playlist.getTracks().getFirst();
                    first.setUserData(requesterTag);
                    callback.trackLoaded(new LavaTrack(first, requesterTag));
                    return;
                }
                List<AudioTrack> all = playlist.getTracks();
                int startIndex = 0;
                AudioTrack selected = playlist.getSelectedTrack();
                if (selected != null) {
                    int idx = all.indexOf(selected);
                    if (idx >= 0) startIndex = idx;
                }
                List<Track> tracks = all.subList(startIndex, all.size()).stream()
                        .limit(maxTracks)
                        .<Track>map(t -> {
                            t.setUserData(requesterTag);
                            return new LavaTrack(t, requesterTag);
                        })
                        .toList();
                callback.playlistLoaded(tracks);
            }

            @Override
            public void noMatches() {
                callback.noMatches();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                callback.loadFailed(exception.getMessage());
            }
        });
    }

    @Override
    public void search(String query, int limit, AudioTrackSearchCallback callback) {
        if (!isTrusted(query)) {
            callback.noMatches();
            return;
        }
        playerManager.loadItem(query, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                callback.searchResults(List.of(toTrackMetadata(track)));
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                List<TrackMetadata> tracks = playlist.getTracks().stream()
                        .limit(limit)
                        .map(LavaAudioTrackLoader::toTrackMetadata)
                        .toList();
                callback.searchResults(tracks);
            }

            @Override
            public void noMatches() {
                callback.noMatches();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                callback.loadFailed(exception.getMessage());
            }
        });
    }

    /**
     * A search ({@code ytsearch:…}) or an http(s) link to a host one of our sources serves. The sources' own link
     * patterns are loose (Bandcamp's accepts {@code http://redis:6379/#.bandcamp.com/track/x}), so without this a
     * pasted link could make the bear fetch an internal address.
     */
    static boolean isTrusted(String query) {
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

    private static TrackMetadata toTrackMetadata(AudioTrack track) {
        return new TrackMetadata(
                track.getInfo().title,
                track.getInfo().author,
                track.getInfo().uri,
                track.getDuration());
    }
}
