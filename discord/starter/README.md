# discord-starter

Spring Boot auto-configuration that bridges `discord-api` with Spring. Provides annotations and wiring so application code can declare commands with zero boilerplate.

## Purpose
Acts as the glue between the Discord abstraction and Spring Boot. Scans `@DiscordController` beans for `@Interaction` methods, builds the command, button and autocomplete registries, and manages the bot's lifecycle as a `SmartLifecycle` bean.

## Usage

```java
@DiscordController
public class PingController {

    @Interaction(value = "ping", description = "Check if the bot is alive")
    public void ping(CommandInteraction interaction) {
        interaction.reply("Pong!").send();
    }
}
```

Declare typed options with `@Option` (`String`, `int`/`Integer`, `long`/`Long`, `boolean`/`Boolean`):

```java
@Interaction(value = "play", description = "Play a track or search YouTube.")
public void play(CommandInteraction interaction,
                 @Option(name = "search", description = "Track name, URL, or search term", required = true) String query) {
    // query is automatically extracted from the interaction options
}
```

Group commands as subcommands with `@InteractionGroup` (this registers `/music play` and `/music skip`):

```java
@DiscordController
@InteractionGroup(value = "music", description = "Music commands")
public class MusicController {

    @Interaction(value = "play", description = "Play a song")
    public void play(CommandInteraction interaction) { ... }

    @Interaction(value = "skip", description = "Skip the current song")
    public void skip(CommandInteraction interaction) { ... }
}
```

Handle button clicks with `type = BUTTON`; the value is the button's custom id:

```java
@Interaction(type = InteractionType.BUTTON, value = "player:pause-play")
public void pausePlay(ButtonInteraction interaction) {
    interaction.acknowledge();
}
```

Answer autocomplete for an `@Option(..., autocomplete = true)` with `type = AUTOCOMPLETE`; the value is `command:option`, or `command:subcommand:option` for grouped commands:

```java
@Interaction(type = InteractionType.AUTOCOMPLETE, value = "player:play:search")
public void search(AutocompleteInteraction interaction) {
    interaction.reply(List.of(new Option("lofi mix", "lofi-mix")));
}
```

## Error Handling

An exception thrown by any handler is logged through SLF4J and answered, so Discord never shows "This interaction failed":
- commands and buttons get an ephemeral "Something went wrong. Please try again later." reply;
- autocomplete gets an empty choice list.

If that reply fails too (for example because the handler already replied), the failure is logged and swallowed.

## Configuration

| Property | Required | Description |
|---|---|---|
| `discord.token` | Yes | Bot token from the Discord Developer Portal |
| `discord.guild-id` | No | Guild ID for instant command registration (dev only) |
| `discord.activity.type` | No | Presence type: `PLAYING`, `LISTENING`, `WATCHING` or `COMPETING` (required when `text` is set) |
| `discord.activity.text` | No | Presence text (required when `type` is set) |

## Contents
- `@DiscordController` — marks a class as a Spring bean holding interaction handlers
- `@InteractionGroup` — groups a controller's commands as subcommands of one command
- `@Interaction` — marks a method as a command, button or autocomplete handler
- `@Option` — binds a method parameter to a slash command option
- `CommandRegistry`, `ButtonRegistry`, `AutocompleteRegistry` — route incoming interactions to the right handler
- `DiscordAutoConfiguration` — Spring Boot auto-configuration
- `DiscordProperties` — validated configuration properties
