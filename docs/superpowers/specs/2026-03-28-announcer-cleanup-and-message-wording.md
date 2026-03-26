# Announcer Cleanup and Message Wording

## Goal

Remove dead code and simplify small design decisions left unresolved after the embed state row implementation.

## Changes

### Dead method removal

`onQueueEmpty()` in `MusicPlayerEventListener` has no callers. It is superseded by `onNothingToAdvance()`. Remove it.

### Emoji constant

An inline emoji character in `MusicPlayerCommand` was not moved to `BearifyEmoji` during the embed state row implementation. Add a readable constant and reference it from the command.

### Footer simplification

`TextChannelMusicPlayerTrackAnnouncer` appends a randomly selected suffix from `FOOTER_ENDINGS` to the footer text. Remove the suffix list and the random selection. The footer will render only the static `properties.footer()` value.

### Action message wording

Two action lines shown in the embed description are confusing because they appear on the *new* track after a track switch, making the message feel like it refers to the track currently playing.

Updated wording:

| Event | Before | After |
|---|---|---|
| `Skipped` | `Skipped by @user` | `Last track skipped by @user` |
| `WentBack` | `Went back by @user` | `Jumped back by @user` |

`Rewound`, `Forwarded`, and `Cleared` are unaffected — they operate on the current track and their wording is not confusing.

## Spec updates

Update `2026-03-26-announcer-embed-state-row-design.md` to reflect:

- the revised action message text for `Skipped` and `WentBack`
- the simplified footer (no random suffixes)
