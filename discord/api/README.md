# discord-api

Pure Java abstraction over any Discord library. Has zero framework or third-party dependencies.

## Purpose
Defines the interfaces and models that the rest of the project depends on. By depending only on this module, application code never sees JDA or any other Discord library directly. Swapping implementations only requires replacing `discord-jda`.

## Contents

### Gateway
- `DiscordClient` — bot client interface: `start(token)`, `start(token, guildId)`, `guild(guildId)`, `selfUserId()`, `textChannel(channelId)`, `shutdown()`
- `DiscordClientFactory` — creates a fully configured `DiscordClient` given commands, an interaction handler, an optional activity and an optional delay after which ephemeral replies delete themselves
- `Guild` — guild-scoped operations:
  - `voice()` — returns the bot's current `VoiceSession` for this guild, or empty if not connected
  - `isInVoice(userId)` — whether that member is in one of the guild's voice channels right now
  - `join(channelId, provider, onJoined)` — joins a voice channel with the given `AudioProvider` and invokes the listener once connected
- `TextChannel` — sends a plain-text message to a Discord text channel via `send(String message)`

### Interaction
- `RepliableInteraction` — an interaction answered with a reply: `reply(message)`, `defer()`, `defer(ephemeral)`, plus `getVoiceChannelId()`, `getGuildId()`, `getTextChannelId()`
- `CommandInteraction` — a `RepliableInteraction` for an incoming command: `getName()`, `getOption(name)`, `getSubcommandName()`
- `MessageContextInteraction` — a `RepliableInteraction` for a message context-menu command (Apps on a message): `getName()`, `getTargetMessageContent()`, `getTargetAuthorMention()`
- `ButtonInteraction` — represents a button press: `getCustomId()`, `acknowledge()`, `reply(message)`, and `reply(ComponentMessage)` for a Components V2 reply (a follow-up once acknowledged)
- `ReplyBuilder` — fluent reply builder: `reply("...").ephemeral().send()`; `keep()` leaves an ephemeral reply until the user dismisses it instead of letting it clean itself up
- `EditableMessage` — handle to a deferred reply that can be updated: `edit(String message)`, or `edit(ComponentMessage)` to turn it into a Components V2 message; `keep()` leaves an ephemeral reply until the user dismisses it

### Voice
- `AudioProvider` — supplies Opus-encoded audio frames for a voice connection: `canProvide()`, `provide20MsAudio()`, `isOpus()`
- `VoiceSession` — represents an active voice connection: `getChannelId()`, `isLonely()`, `hasListeners()`, `setStatus(status)`, `leave()`
- `VoiceSessionListener` — callback fired once the bot has joined a voice channel: `onJoined(channelId)`

### Model
- `CommandDefinition` — describes a command registered with Discord (name + description + subcommands + options + type): a `SLASH` command, or a `MESSAGE` context-menu command made with `CommandDefinition.message(name)`
- `OptionDefinition` — describes a command option (name, type, description, required)
