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

## Approved Message Catalog

This section captures the approved slash-command messages by state for the current controller surface.

### Shared Validation Messages

- not in voice channel: `Looks like you're not in a voice channel! Jump in one and try again.`
- not in guild: `I only lumber around inside a server.`
- no player in your voice channel: `Seems like I'm not even playing songs! If you'd like me to, have you tried the /player play command?`

### `/player join`

- initial deferred status: `Sending a bear your way!`
- success when ready: `Joined <#channel>, [random celebratory ending]`
- no players available: `No music bears are free right now. Try again in a moment.`
- connect failed: `The bear couldn't reach your channel. Try again in a moment.`

### `/player leave`

- success: `Alright, cleaning up after myself. Talk to you later!`
- no player in your voice channel: `Seems like I'm not even playing songs! If you'd like me to, have you tried the /player play command?`
- player is active elsewhere in the guild, not with the caller: `Bear with me, I can't lumber out of your voice channel when I'm not with you.`

### `/player play`

- initial deferred status while joining first: `Sending a bear your way!`
- success after queueing: `@user added your track to the mix.`
- no players available: `Bearly any bears left! They're all busy right now. Try again in a moment.`
- connect failed: `The bear couldn't reach your channel. Try again in a moment.`
- track not found: `I couldn't sniff out that track. Try a different search or link?`
- track load failed: `Oops! Something went wrong loading this track. This could be due to age-restrictions or region lock. (I'm technically less than 1 year old)`

### Ephemeral Playback Control Successes

These messages should read as personal confirmations with light bear tone, not room narration.

#### `/player pause`

- paused: `Paused the current track for you.`
- resumed: `Resumed the current track for you.`

#### `/player previous`

- success: `Back on the trail to the previous track.`
- nothing to go back to: `This is where the trail begins. Nothing to go back to!`

#### `/player next`

- success: `On to the next track.`
- queue empty: `This is where the trail ends. Try adding more using /player play.`

#### `/player rewind`

- success without duration: `Rewound the current track for you.`
- success with duration: `Rewound the current track by <seconds>s for you.`
- zero seconds: `Bold strategy. Rewinding by exactly 0 seconds.`

#### `/player forward`

- success without duration: `Forwarded the current track for you.`
- success with duration: `Forwarded the current track by <seconds>s for you.`
- zero seconds: `Bold strategy. Forwarding by exactly 0 seconds.`
- queue empty: `This is where the trail ends. Try adding more using /player play.`

#### `/player clear`

- success: `Cleared the playlist. Fresh paws.`

### `/poke`

- keep the existing latency transcript and summary wording unchanged
- make the full interaction ephemeral

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
