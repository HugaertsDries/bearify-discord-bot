# Announcer Embed State Row Design

## Goal

Add a dynamic status area to the music announcer embed so the channel can see persistent playback state and short-lived control actions without relying on public slash-command acknowledgements.

## Problem

The announcer embed currently posts now-playing and error information, but it does not represent routine playback controls such as pause, skip, rewind, forward, previous, or clear.

That creates a gap after slash-command responses move to ephemeral:

1. The channel loses visibility into meaningful shared playback changes.
2. Making slash-command replies public again would push the announcer embed upward and recreate channel clutter.

The announcer needs to become the canonical public playback surface for both state and lightweight action feedback.

## Recommended Approach

Use the announcer embed description as a dynamic status area with two layers:

- a persistent playback-state row
- a temporary action row that auto-clears after a configurable timeout

The player event stream remains the source of truth. The controller announcer should render from music-player events instead of inferring actions from slash-command handling.

## Status Model

### Persistent Playback State

The embed always shows one persistent playback-state row while the embed exists:

- `On the air`
- `Taking a break`

This row remains visible until a real playback-state event changes it.

### Temporary Action Row

The embed may also show one temporary action row beneath the persistent state:

- `Last track skipped by @user`
- `Jumped back by @user`
- `Rewound by @user`
- `Forwarded by @user`
- `Cleared by @user`

Rules:

- the temporary row is replaced by newer actions
- the temporary row auto-clears after a timeout
- the temporary row is not accumulated as history
- `play` does not create a temporary row

## Rendering

The embed description becomes the dynamic status area.

Examples:

### Playing

```text
On the air
```

### Paused

```text
Taking a break
```

### Playing with temporary action

```text
On the air
Last track skipped by @user
```

### Paused with temporary action

```text
Taking a break
Cleared by @user
```

The title, author, requester, author metadata, and length remain unchanged.

Visuals:

- playing uses `VibingGIF` as the main image
- paused uses `WaitingGIF` as the main image
- the disk thumbnail remains unchanged for both states

## Event Source

The announcer should be driven from the player event stream, not controller-side command knowledge.

That keeps the player as the source of truth for:

- playback state
- successful control actions
- the acting user to display publicly

## Required Event Model Changes

The current event model is not sufficient for the announcer design because only `TrackStart` includes requester identity, and the control-success events needed for announcer action lines are missing or do not include actor data.

Extend `MusicPlayerEvent` with enough information to render the announcer state/action surface directly.

### Persistent State Events

- `TrackStart(..., requesterTag)` sets persistent state to `On the air`
- `Paused(..., requesterTag)` sets persistent state to `Taking a break`
- `Resumed(..., requesterTag)` sets persistent state to `On the air`

### Temporary Action Events

Add player-emitted success events for:

- next
- previous
- rewind
- forward
- clear

Each event must include the acting user tag so the announcer can render `... by @user`.

Suggested shape:

- `Skipped(playerId, requestId, guildId, requesterTag)`
- `WentBack(playerId, requestId, guildId, requesterTag)`
- `Rewound(playerId, requestId, guildId, requesterTag, seekMs)`
- `Forwarded(playerId, requestId, guildId, requesterTag, seekMs)`
- `Cleared(playerId, requestId, guildId, requesterTag)`

The exact names can be adjusted during implementation, but the model must remain explicit and typed.

## Announcer State Handling

`TextChannelMusicPlayerTrackAnnouncer` should keep local announcer-only state:

- current persistent playback state
- current temporary action text
- active clear timer for the temporary action

Behavior:

1. On `TrackStart`, update embed content and set persistent state to `On the air`.
2. On `Paused`, update persistent state to `Taking a break`.
3. On `Resumed`, update persistent state to `On the air`.
4. On action-success event, set temporary action row and start/reset the clear timer.
5. On timer expiry, clear only the temporary action row and update the embed.
6. On actual playback exhaustion or `Stopped`, delete the embed and clear all announcer state.

Playback semantics:

- `next` and `previous` should unpause when they switch to a different track
- `rewind` and `forward` should preserve paused state
- a failed `next` because no queued track exists must not emit the same terminal event used for actual playback exhaustion
- `QueueEmpty` should only represent actual end-of-playback exhaustion, not "skip failed because there was no next track"

## Timeout

The temporary action row clears automatically after a configurable timeout.

Default:

- `15s`

Requirement:

- timeout must be configurable through announcer properties

This timeout only affects the temporary action row. It must not clear the persistent `On the air` or `Taking a break` state row.

## Concurrency and Safety

The announcer needs simple timer lifecycle management:

- replacing a temporary action cancels the old clear timer
- deleting the embed cancels any pending clear timer
- timer completion must not resurrect a deleted embed

This does not require a live progress loop or frequent periodic updates. The only background work is clearing the temporary action row after timeout.

## Non-Goals

- live playback progress bar
- periodic ticking updates
- public action history log
- public action row for `/player play`
- exposing announcer state through slash commands

## Supporting Refactor

`MusicPlayerCommand` currently embeds emoji characters inline in message strings.

As part of the implementation, move those emoji literals into `BearifyEmoji` with human-readable constant names and reuse them from command and announcer message builders instead of keeping raw inline characters in strings.

Requirements:

- add readable emoji constants only for the characters actually used by the updated controller command and announcer paths
- prefer reuse over duplicate constants with overlapping meaning
- keep the refactor scoped to this feature work instead of rewriting unrelated message surfaces

## Testing Impact

Tests should verify:

- `TrackStart` renders `On the air`
- `Paused` renders `Taking a break`
- `Resumed` restores `On the air`
- action-success events render the correct `... by @user` temporary row
- a new temporary action replaces an older one
- the temporary row clears after the configured timeout
- actual playback exhaustion and `Stopped` still delete the embed
- timer expiry does not recreate a deleted embed
- `next` and `previous` unpause when changing tracks
- `rewind` and `forward` preserve paused state
- failed `next` with no queued track does not delete the embed or emit terminal playback exhaustion
- emoji extraction preserves the approved visible message text while replacing inline literals with `BearifyEmoji` constants in code

## Rationale

This design gives each layer a single job:

- slash-command responses are private command feedback
- player events are the source of truth
- the announcer embed is the only public playback surface

That preserves embed visibility, keeps public playback context in one place, and avoids inventing controller-side approximations of player state.
