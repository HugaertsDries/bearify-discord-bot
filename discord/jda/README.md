# discord-jda

JDA 5 implementation of the `discord-api` abstractions. Auto-configures itself as a Spring Boot starter.

## Purpose
Provides a JDA-backed `DiscordClientFactory` bean. Activated automatically when JDA is on the classpath and no other `DiscordClientFactory` bean is present.

## How it works
1. `JdaAutoConfiguration` registers a `JdaDiscordClientFactory` bean
2. `discord-starter` calls `factory.create(commands, handler)` to get a `JdaDiscordClient`
3. On `start(token)` or `start(token, guildId)`, the client connects to Discord and registers commands

## Contents
- `JdaAutoConfiguration` — registers the factory bean
- `JdaDiscordClientFactory` — creates `JdaDiscordClient` instances
- `JdaDiscordClient` — JDA-backed `DiscordClient` implementation
- `JdaEventListener` — bridges JDA events to the `CommandRegistry` dispatcher
- `JdaCommandInteraction` — wraps `SlashCommandInteractionEvent` as `CommandInteraction`
- `JdaReplyBuilder` — fluent reply builder backed by JDA's reply API

## Dependencies
- `discord-starter`
- `net.dv8tion:JDA:5.3.0`
