package com.bearify.discord.api.gateway;

/**
 * Abstraction over a Discord library's bot client.
 * Swap out implementations (JDA, Discord4J, …) without touching application code.
 *
 * <p>Instances are created via {@link DiscordClientFactory}, which receives commands and
 * the interaction handler at construction time.
 */
public interface DiscordClient {

    /** Connect to Discord globally using the given token. */
    void start(String token);

    /** Connect to Discord and register commands to a specific guild (instant update — great for dev). */
    void start(String token, String guildId);

    /** Access guild-scoped capabilities. */
    Guild guild(String guildId);

    /** Disconnect and release resources. */
    void shutdown();
}
