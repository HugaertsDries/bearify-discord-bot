# discord-api

Pure Java abstraction over any Discord library. Has zero framework or third-party dependencies.

## Purpose
Defines the interfaces and models that the rest of the project depends on. By depending only on this module, application code never sees JDA or any other Discord library directly. Swapping implementations only requires replacing `discord-jda`.

## Contents

### Gateway
- `DiscordClient` — bot client interface: `start(token)`, `start(token, guildId)`, `guild(guildId)`, `selfUserId()`, `textChannel(channelId)`, `shutdown()`
- `DiscordClientFactory` — creates a fully configured `DiscordClient` given commands and an interaction handler
- `Guild` — guild-scoped operations:
  - `voice()` — returns the bot's current `VoiceSession` for this guild, or empty if not connected
  - `isInVoice(userId)` — whether that member is in one of the guild's voice channels right now
  - `join(channelId, provider, onJoined)` — joins a voice channel with the given `AudioProvider` and invokes the listener once connected
- `TextChannel` — sends a plain-text message to a Discord text channel via `send(String message)`

### Interaction
- `CommandInteraction` — represents an incoming command: `getName()`, `getOption(name)`, `getSubcommandName()`, `getVoiceChannelId()`, `getGuildId()`, `getTextChannelId()`, `reply(message)`, `defer()`
- `ButtonInteraction` — represents a button press: `getCustomId()`, `acknowledge()`, `reply(message)`, and `reply(ComponentMessage)` for a Components V2 reply (a follow-up once acknowledged)
- `ReplyBuilder` — fluent reply builder: `reply("...").ephemeral().send()`
- `EditableMessage` — handle to a deferred reply that can be updated: `edit(String message)`, or `edit(ComponentMessage)` to turn it into a Components V2 message

### Voice
- `AudioProvider` — supplies Opus-encoded audio frames for a voice connection: `canProvide()`, `provide20MsAudio()`, `isOpus()`
- `VoiceSession` — represents an active voice connection: `getChannelId()`, `isLonely()`, `hasListeners()`, `leave()`
- `VoiceSessionListener` — callback fired once the bot has joined a voice channel: `onJoined(channelId)`

### Model
- `CommandDefinition` — describes a command registered with Discord (name + description + subcommands + options)
- `OptionDefinition` — describes a command option (name, type, description, required)
