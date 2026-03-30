package com.bearify.controller.music.discord;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("bearify.music.announcer")
public record AnnouncerProperties(
        @DefaultValue("#FFA500") String primaryColor,
        @DefaultValue("#FF4444") String errorColor,
        @DefaultValue("Bearify \u2022 Powered by Bearable Software") String footer,
        @DefaultValue("15s") Duration actionTimeout
) {
    public AnnouncerProperties {
        Integer.decode(primaryColor);
        Integer.decode(errorColor);
    }

    public int colorNowPlayingInt() {
        return Integer.decode(primaryColor);
    }

    public int colorErrorInt() {
        return Integer.decode(errorColor);
    }
}
