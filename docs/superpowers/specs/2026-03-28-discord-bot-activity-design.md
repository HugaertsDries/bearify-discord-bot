# Discord Bot Activity Configuration

## Goal

Add an optional, fixed startup activity for each bot so the controller and player can expose different Discord presence text through configuration without introducing runtime updates or templating.

## Scope

This design covers:

- configuration shape and validation for bot activity
- startup wiring from Spring configuration into the Discord client
- JDA mapping from configured activity to Discord presence
- test coverage for property binding and adapter behavior

This design does not cover:

- dynamic activity updates after startup
- templated activity text using runtime values such as `player.id`
- streaming activities that require a URL
- status changes such as idle, do-not-disturb, or invisible

## Configuration

Add an optional nested `activity` block under the existing `discord` properties:

```yaml
discord:
  token: ${DISCORD_CONTROLLER_TOKEN}
  guild-id: ${DISCORD_GUILD_ID:}
  activity:
    type: PLAYING
    text: "with slash commands"
```

If `discord.activity` is absent, the bot keeps its current behavior and starts without any configured activity.

If `discord.activity` is present, both fields are required:

- `type`: one of `PLAYING`, `LISTENING`, `WATCHING`, `COMPETING`
- `text`: non-blank activity text

This keeps the initial feature simple while avoiding a future config redesign when different activity types are needed.

## Architecture

The source of truth remains `discord-starter`, because that module already owns bot startup configuration through `DiscordProperties`.

The implementation will introduce a small activity configuration model that flows through startup like this:

1. `DiscordProperties` binds and validates the optional nested `activity` block.
2. `DiscordAutoConfiguration` passes the resolved activity configuration into the client creation path.
3. `DiscordClientFactory` accepts the optional activity configuration when creating a `DiscordClient`.
4. `JdaDiscordClientFactory` forwards the activity configuration into `JdaDiscordClient`.
5. `JdaDiscordClient` applies the configured activity during connection startup.

This preserves the current separation:

- `discord-starter` owns configuration and lifecycle wiring
- `discord-jda` owns JDA-specific translation into Discord presence objects

## JDA Behavior

`JdaDiscordClient` will map the configured activity type to JDA activity factories:

- `PLAYING` -> `Activity.playing(text)`
- `LISTENING` -> `Activity.listening(text)`
- `WATCHING` -> `Activity.watching(text)`
- `COMPETING` -> `Activity.competing(text)`

The activity is fixed at startup. Once the client has connected, the configured activity remains unchanged unless the application restarts.

The activity should be applied as part of client startup, using the same startup path whether commands are registered globally or for a single guild.

## Validation And Error Handling

Validation should fail fast before JDA startup when configuration is invalid.

Rules:

- missing `discord.activity` is valid and means disabled
- present `discord.activity` with missing `type` is invalid
- present `discord.activity` with missing or blank `text` is invalid
- unknown `type` values should fail property binding

This keeps failures deterministic and avoids silently starting the bot with incomplete presence configuration.

## Application Configuration

Each bot can set its own startup activity in its own `application.yml`.

Examples:

Controller:

```yaml
discord:
  token: ${DISCORD_CONTROLLER_TOKEN}
  guild-id: ${DISCORD_GUILD_ID:}
  activity:
    type: PLAYING
    text: "with slash commands"
```

Player:

```yaml
discord:
  token: ${DISCORD_PLAYER_TOKEN}
  guild-id: ${DISCORD_GUILD_ID}
  activity:
    type: LISTENING
    text: "your queue"
```

No shared cross-bot logic is needed beyond the common property model and client wiring.

## Testing

Add coverage in two places.

`discord-starter`:

- property binding succeeds when `discord.activity.type` and `discord.activity.text` are both valid
- property binding fails when `text` is blank
- property binding fails when `activity` is partially configured
- omitting `activity` keeps startup behavior unchanged

`discord-jda`:

- each supported activity type maps to the expected JDA `Activity`
- absence of activity leaves the client without configured startup activity

Tests should stay focused on configuration correctness and adapter mapping, not on live Discord behavior.

## Implementation Notes

- Keep the activity model small and explicit rather than accepting arbitrary strings.
- Do not overload the existing `start(token)` and `start(token, guildId)` methods with extra parameters; the activity should be part of client construction, not runtime start arguments.
- Update README or module documentation only if the repository currently documents bot configuration in those locations during implementation.

## Success Criteria

The feature is complete when:

- each bot can declare its own fixed startup activity through configuration
- invalid activity configuration fails startup clearly
- bots with no activity configuration behave exactly as they do today
- JDA startup applies the configured activity consistently for both global and guild command registration modes
