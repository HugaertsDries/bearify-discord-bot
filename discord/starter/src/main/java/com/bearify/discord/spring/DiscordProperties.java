package com.bearify.discord.spring;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Optional;

/**
 * Configuration properties for the Discord bot.
 *
 * <pre>
 * discord:
 *   token: "your-bot-token"
 *   guild-id: "123456789"   # optional — set for instant command updates during dev
 * </pre>
 */
@ConfigurationProperties(prefix = "discord")
@Validated
public final class DiscordProperties {

    @NotBlank
    private final String token;
    private final String guildId;

    public DiscordProperties(String token, String guildId) {
        this.token = token;
        this.guildId = guildId;
    }

    public String token() {
        return token;
    }

    /**
     * Optional guild (server) ID for registering commands.
     * When present, commands are registered per-guild (instant update — great for dev).
     * When absent, commands are registered globally (up to 1 hour propagation delay).
     */
    public Optional<String> guildId() {
        return Optional.ofNullable(guildId);
    }
}
