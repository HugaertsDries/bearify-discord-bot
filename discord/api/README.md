# discord-api

Pure Java abstraction over any Discord library. Has zero framework or third-party dependencies.

## Purpose
Defines the interfaces and models that the rest of the project depends on. By depending only on this module, application code never sees JDA or any other Discord library directly. Swapping implementations only requires replacing `discord-jda`.

## Contents

### Gateway
- `DiscordClient` — bot client interface: `start(token)`, `start(token, guildId)`, `shutdown()`
- `DiscordClientFactory` — creates a fully configured `DiscordClient` given commands and an interaction handler

### Interaction
- `CommandInteraction` — represents an incoming command: `getName()`, `reply(message)`
- `ReplyBuilder` — fluent reply builder: `reply("...").ephemeral().send()`

### Model
- `CommandDefinition` — describes a command registered with Discord (name + description)
