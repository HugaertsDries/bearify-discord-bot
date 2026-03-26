# Controller Command Response Visibility Design

## Goal

Define when controller slash-command responses should be public versus ephemeral, and how public playback context should be represented without drowning the announcer embed in command acknowledgements.

## Problem

The controller currently mixes public and ephemeral slash-command responses across `player` and `poke`.

That creates two issues:

1. Routine playback acknowledgements post into the channel and push the announcer embed upward.
2. If those acknowledgements become ephemeral, some shared playback changes lose their public surface.

The design needs one clear rule for slash-command visibility and one clear public surface for shared playback state.

## Recommended Approach

Use the announcer embed as the canonical public playback surface.

Use slash-command responses as personal feedback to the invoker by default.

Only keep a slash-command response public when it communicates a channel-level lifecycle transition that the announcer flow does not already represent well.

## Visibility Rule

### Public

Slash-command responses may be public only for channel-level lifecycle transitions:

- player joined the voice channel
- player left the voice channel

These are infrequent, room-level changes that are not routine playback controls.

### Ephemeral

Slash-command responses should be ephemeral for:

- validation failures
- command failures
- diagnostics
- queue/load outcomes
- routine playback controls

This includes both direct replies and deferred/edit flows.

## Command Mapping

### `/player join`

- success: public
- no voice channel: ephemeral
- not in guild: ephemeral
- no players available: ephemeral
- connect failed: ephemeral

### `/player leave`

- success: public
- no voice channel: ephemeral
- not in guild: ephemeral
- no player in channel: ephemeral

### `/player play`

- success: ephemeral
- no voice channel: ephemeral
- not in guild: ephemeral
- no players available: ephemeral
- connect failed: ephemeral
- track not found: ephemeral
- track load failed: ephemeral

### `/player pause`

- paused: ephemeral
- resumed: ephemeral
- no player in channel: ephemeral
- invalid context: ephemeral

### `/player previous`

- success: ephemeral
- nothing to go back to: ephemeral
- no player in channel: ephemeral
- invalid context: ephemeral

### `/player next`

- success: ephemeral
- queue empty: ephemeral
- no player in channel: ephemeral
- invalid context: ephemeral

### `/player rewind`

- success: ephemeral
- no player in channel: ephemeral
- invalid context: ephemeral

### `/player forward`

- success: ephemeral
- queue empty: ephemeral
- no player in channel: ephemeral
- invalid context: ephemeral

### `/player clear`

- success: ephemeral
- no player in channel: ephemeral
- invalid context: ephemeral

### `/poke`

- all output: ephemeral

## Announcer Policy

The announcer embed is the only public surface for routine playback updates.

When a routine control action materially changes the shared listening experience, the announcer embed may receive a short-lived annotation instead of relying on a public slash-command response.

These annotations should be replaced by the next meaningful announcer update rather than accumulating as history.

### Short-Lived Public Annotations

Recommended:

- paused by `@user`
- resumed by `@user`
- skipped by `@user`
- went back by `@user`
- playlist cleared by `@user`

Usually not needed:

- rewind by `@user`
- forward by `@user`

Small seeks are typically control noise rather than room-level events. If seek actions later prove important, they should still use short-lived annotations rather than public slash-command replies.

## Rationale

This design gives each surface one job:

- slash-command responses confirm command outcomes to the invoker
- announcer embed communicates public playback state to the room

That keeps the channel cleaner, preserves announcer visibility, and avoids duplicating the same playback event across multiple public messages.

## Testing Impact

Tests should verify:

- public versus ephemeral visibility per command outcome
- deferred playback acknowledgements for routine controls are ephemeral
- join and leave success responses remain public
- announcer updates, when implemented, use temporary annotations rather than extra public acknowledgement messages

## Non-Goals

- redesigning announcer embed visuals
- introducing a persistent public audit log of playback commands
- making every playback control publicly visible
