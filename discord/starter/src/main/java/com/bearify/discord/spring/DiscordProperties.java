package com.bearify.discord.spring;

import com.bearify.discord.api.gateway.Activity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @Valid
    private final Activity activity;

    public DiscordProperties(String token, String guildId, Activity activity) {
        this.token = token;
        this.guildId = guildId;
        this.activity = activity;
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
        return Optional
                .ofNullable(guildId)
                .map(String::trim)
                .filter(value -> !value.isBlank());
    }

    public Optional<com.bearify.discord.api.gateway.Activity> activity() {
        return Optional.ofNullable(activity)
                .map(value -> new com.bearify.discord.api.gateway.Activity(value.type(), value.text()));
    }

    public static final class Activity {

        @NotNull
        private final com.bearify.discord.api.gateway.Activity.Type type;
        @NotBlank
        private final String text;

        public Activity(com.bearify.discord.api.gateway.Activity.Type type, String text) {
            this.type = type;
            this.text = text;
        }

        public com.bearify.discord.api.gateway.Activity.Type type() {
            return type;
        }

        public String text() {
            return text;
        }
    }
}
