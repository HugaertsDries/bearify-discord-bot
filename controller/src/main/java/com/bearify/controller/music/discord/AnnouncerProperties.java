package com.bearify.controller.music.discord;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("bearify.music.announcer")
public record AnnouncerProperties(
        @DefaultValue("#FFA500") String colorNowPlaying,
        @DefaultValue("#FF4444") String colorError,
        @DefaultValue("\uD83D\uDC3B Bearify \u2022 Powered by Bearable Software") String footer,
        @DefaultValue("15s") Duration actionTimeout
) {
    public AnnouncerProperties {
        Integer.decode(colorNowPlaying);
        Integer.decode(colorError);
    }

    public int colorNowPlayingInt() {
        return Integer.decode(colorNowPlaying);
    }

    public int colorErrorInt() {
        return Integer.decode(colorError);
    }
}
