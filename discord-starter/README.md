# discord-starter

Spring Boot auto-configuration that bridges `discord-api` with Spring. Provides annotations and wiring so application code can declare commands with zero boilerplate.

## Purpose
Acts as the glue between the Discord abstraction and Spring Boot. Scans for `@Command` beans, builds a `CommandRegistry`, and manages the bot's lifecycle as a `SmartLifecycle` bean.

## Usage

```java
@Command
public class PingCommand {

    @Interaction(value = "ping", description = "Check if the bot is alive")
    public void ping(CommandInteraction interaction) {
        interaction.reply("Pong!").send();
    }
}
```

Group commands as subcommands by giving `@Command` a name:

```java
@Command("music")
public class MusicCommand {

    @Interaction(value = "play", description = "Play a song")
    public void play(CommandInteraction interaction) { ... }

    @Interaction(value = "skip", description = "Skip the current song")
    public void skip(CommandInteraction interaction) { ... }
}
```

## Global Exception Handling

```java
@DiscordControllerAdvice
public class GlobalExceptionHandler {

    @HandleException(RuntimeException.class)
    public void onRuntimeException(CommandInteraction interaction, RuntimeException e) {
        interaction.reply("Something went wrong.").ephemeral().send();
    }
}
```

## Configuration

| Property | Required | Description |
|---|---|---|
| `discord.token` | Yes | Bot token from the Discord Developer Portal |
| `discord.guild-id` | No | Guild ID for instant command registration (dev only) |

## Contents
- `@Command` — marks a class as a Discord command (optionally grouped)
- `@Interaction` — marks a method as an interaction handler
- `@DiscordControllerAdvice` — marks a class as a global exception handler
- `@HandleException` — marks a method as handling a specific exception type
- `CommandRegistry` — routes incoming interactions to the right handler
- `CommandExceptionHandlerRegistry` — routes exceptions to the right handler
- `DiscordAutoConfiguration` — Spring Boot auto-configuration
- `DiscordProperties` — validated configuration properties
