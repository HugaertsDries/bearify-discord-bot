# discord-jda

JDA 5 implementation of the `discord-api` abstractions. Auto-configures itself as a Spring Boot starter.

## Purpose
Provides a JDA-backed `DiscordClientFactory` bean. Activated automatically when JDA is on the classpath and no other `DiscordClientFactory` bean is present.

## How it works
1. `JdaAutoConfiguration` registers a `JdaDiscordClientFactory` bean
2. `discord-starter` calls `factory.create(commands, handler)` to get a `JdaDiscordClient`
3. On `start(token)` or `start(token, guildId)`, the client connects to Discord and registers commands
4. Slash command interactions are dispatched asynchronously via a virtual-thread executor so they never block JDA's event thread

## Contents
- `JdaAutoConfiguration` — registers the factory bean
- `JdaDiscordClientFactory` — creates `JdaDiscordClient` instances
- `JdaDiscordClient` — JDA-backed `DiscordClient`; exposes `guild(guildId)` and `textChannel(channelId)`
- `JdaEventListener` — bridges JDA events to the `CommandRegistry` dispatcher (async, one virtual thread per interaction)
- `JdaCommandInteraction` — wraps `SlashCommandInteractionEvent` as `CommandInteraction`
- `JdaReplyBuilder` — fluent reply builder backed by JDA's reply API
- `JdaEditableMessage` — wraps `InteractionHook` as `EditableMessage` (also enforces the 3-second Discord acknowledgement window)
- `JdaGuild` — JDA-backed `Guild`; `join(channelId, provider, onJoined)` sets the audio send handler before opening the voice connection
- `JdaVoiceSession` — wraps JDA's `AudioManager` as `VoiceSession`
- `JdaTextChannel` — wraps JDA's `MessageChannel` as `TextChannel`

## Dependencies
- `discord-starter`
- `net.dv8tion:JDA:5.3.0`
