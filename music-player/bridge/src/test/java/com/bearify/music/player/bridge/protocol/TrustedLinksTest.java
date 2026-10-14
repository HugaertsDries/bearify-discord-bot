package com.bearify.music.player.bridge.protocol;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedLinksTest {

    // happy path

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=abc",
            "https://m.youtube.com/watch?v=abc",
            "https://music.youtube.com/watch?v=abc",
            "https://youtu.be/abc",
            "https://soundcloud.com/artist/song",
            "https://on.soundcloud.com/abc",
            "https://artist.bandcamp.com/track/song",
            "https://vimeo.com/123",
            "https://www.twitch.tv/channel",
            "ytsearch:lofi beats https://example.com"
    })
    void trustsSearchesAndLinksToTheSourcesHosts(String query) {
        assertThat(TrustedLinks.isTrusted(query)).isTrue();
    }

    // edge cases

    @ParameterizedTest
    @ValueSource(strings = {
            "http://redis:6379/#.bandcamp.com/track/x",
            "http://[::1]:8080/#.bandcamp.com/track/x",
            "https://[::1]/watch?v=abc",
            "https://evil.example@youtube.com/watch?v=abc",
            "https://on-soundcloud.com/abc",
            "https://bandcamp.com.evil.example/track/x",
            "https://youtube.com/watch?v=abc#.evil.example",
            "ftp://youtube.com/watch?v=abc",
            "https://not a link"
    })
    void distrustsLinksOutsideTheSourcesHosts(String link) {
        assertThat(TrustedLinks.isTrusted(link)).isFalse();
    }
}
