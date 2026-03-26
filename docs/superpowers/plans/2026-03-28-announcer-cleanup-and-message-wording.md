# Announcer Cleanup and Message Wording Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove dead code, simplify the footer, extract an emoji constant, and fix two confusing action message strings in the track announcer.

**Architecture:** Four independent, self-contained changes in the controller module. No new types introduced. No cross-module changes.

**Tech Stack:** Java 25, Spring Boot 3.4.3, JUnit 5, AssertJ

---

## Files

| File | Change |
|---|---|
| `controller/src/main/java/com/bearify/controller/music/domain/MusicPlayerEventListener.java` | Remove dead `onQueueEmpty()` method |
| `controller/src/main/java/com/bearify/controller/format/BearifyEmoji.java` | Add `NOTES` constant |
| `controller/src/main/java/com/bearify/controller/music/discord/MusicPlayerCommand.java` | Replace inline `\uD83C\uDFB6` with `BearifyEmoji.NOTES` |
| `controller/src/main/java/com/bearify/controller/music/discord/TextChannelMusicPlayerTrackAnnouncer.java` | Remove `FOOTER_ENDINGS`, `RANDOM`, simplify `footer()`; update two action strings; remove TODO comments |
| `controller/src/test/java/com/bearify/controller/music/discord/TextChannelMusicPlayerTrackAnnouncerTest.java` | Update `Skipped` assertion; add `WentBack` test |
| `docs/superpowers/specs/2026-03-26-announcer-embed-state-row-design.md` | Update spec to reflect revised wording and footer |

---

### Task 1: Remove dead `onQueueEmpty()` method

**Files:**
- Modify: `controller/src/main/java/com/bearify/controller/music/domain/MusicPlayerEventListener.java`

- [ ] **Step 1: Remove the method and its TODO comment**

  In `MusicPlayerEventListener.java`, delete these two lines:

  ```java
  // TODO AI is still used? if not remove it
  default void onQueueEmpty() {}
  ```

  The file should end with:

  ```java
  public interface MusicPlayerEventListener {
      default void onReady() {}
      default void onFailed(String reason) {}
      default void onNoPlayersAvailable() {}
      default void onTrackNotFound(String query) {}
      default void onTrackLoadFailed(String reason) {}
      default void onPaused() {}
      default void onResumed() {}
      default void onNothingToAdvance() {}
      default void onNothingToGoBack() {}
  }
  ```

- [ ] **Step 2: Run the controller tests to confirm nothing breaks**

  ```bash
  JAVA_HOME="/c/Users/dries/.jdks/openjdk-25.0.2" ./gradlew :controller:test -q
  ```

  Expected: BUILD SUCCESSFUL, no failures.

- [ ] **Step 3: Commit**

  ```bash
  git add controller/src/main/java/com/bearify/controller/music/domain/MusicPlayerEventListener.java
  git commit -m "remove dead onQueueEmpty method"
  ```

---

### Task 2: Extract `NOTES` emoji constant

**Files:**
- Modify: `controller/src/main/java/com/bearify/controller/format/BearifyEmoji.java`
- Modify: `controller/src/main/java/com/bearify/controller/music/discord/MusicPlayerCommand.java`

- [ ] **Step 1: Add `NOTES` to `BearifyEmoji`**

  In `BearifyEmoji.java`, add after the `MUSIC` constant:

  ```java
  public static final String NOTES = "\uD83C\uDFB6";
  ```

  The constants block should read:

  ```java
  public static final String BEAR = "\uD83D\uDC3B";
  public static final String PAW = "\uD83D\uDC3E";
  public static final String HONEY = "\uD83C\uDF6F";
  public static final String MUSIC = "\uD83C\uDFB5";
  public static final String NOTES = "\uD83C\uDFB6";
  public static final String SPEAKER = "\uD83D\uDD0A";
  // ... rest unchanged
  ```

- [ ] **Step 2: Use `BearifyEmoji.NOTES` in `MusicPlayerCommand`**

  In `MusicPlayerCommand.java`, in the `JOIN_ENDINGS` list, replace the last entry:

  Old:
  ```java
  // TODO add this emoji also to BearifyEmoji
  "Let's hibernate to some good beats! \uD83C\uDFB6"
  ```

  New:
  ```java
  "Let's hibernate to some good beats! " + BearifyEmoji.NOTES
  ```

- [ ] **Step 3: Run the controller tests**

  ```bash
  JAVA_HOME="/c/Users/dries/.jdks/openjdk-25.0.2" ./gradlew :controller:test -q
  ```

  Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

  ```bash
  git add controller/src/main/java/com/bearify/controller/format/BearifyEmoji.java \
          controller/src/main/java/com/bearify/controller/music/discord/MusicPlayerCommand.java
  git commit -m "extract NOTES emoji constant"
  ```

---

### Task 3: Remove randomized footer endings

**Files:**
- Modify: `controller/src/main/java/com/bearify/controller/music/discord/TextChannelMusicPlayerTrackAnnouncer.java`

- [ ] **Step 1: Remove `FOOTER_ENDINGS`, `RANDOM`, and simplify `footer()`**

  In `TextChannelMusicPlayerTrackAnnouncer.java`:

  1. Remove the `Random` import:
     ```java
     import java.util.Random;
     ```

  2. Remove the two static fields (and their TODO comment):
     ```java
     // TODO AI I've reconciderd this, remove the randomized FOOTER endings.
     private static final List<String> FOOTER_ENDINGS = List.of(
             "Certified banger detector™",
             "Press play, regret nothing",
             "This is un-bear-ably good",
             "100% bug-free (probably)",
             "Compiled with love",
             "Running on caffeine & vibes"
     );
     ```
     and:
     ```java
     private static final Random RANDOM = new Random();
     ```

  3. Replace the `footer()` method body:

     Old:
     ```java
     private String footer() {
         return properties.footer() + " • " + FOOTER_ENDINGS.get(RANDOM.nextInt(FOOTER_ENDINGS.size()));
     }
     ```

     New:
     ```java
     private String footer() {
         return properties.footer();
     }
     ```

- [ ] **Step 2: Run the controller tests**

  ```bash
  JAVA_HOME="/c/Users/dries/.jdks/openjdk-25.0.2" ./gradlew :controller:test -q
  ```

  Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

  ```bash
  git add controller/src/main/java/com/bearify/controller/music/discord/TextChannelMusicPlayerTrackAnnouncer.java
  git commit -m "remove randomized footer endings from announcer"
  ```

---

### Task 4: Fix confusing action message wording

**Files:**
- Modify: `controller/src/test/java/com/bearify/controller/music/discord/TextChannelMusicPlayerTrackAnnouncerTest.java`
- Modify: `controller/src/main/java/com/bearify/controller/music/discord/TextChannelMusicPlayerTrackAnnouncer.java`
- Modify: `docs/superpowers/specs/2026-03-26-announcer-embed-state-row-design.md`

- [ ] **Step 1: Update the existing `Skipped` test assertion**

  In `TextChannelMusicPlayerTrackAnnouncerTest.java`, in `acceptShowsTemporarySkippedAction()`, change line 77:

  Old:
  ```java
  assertThat(updated.get().description()).hasValue(PLAYING_STATUS + System.lineSeparator() + "Skipped by @user");
  ```

  New:
  ```java
  assertThat(updated.get().description()).hasValue(PLAYING_STATUS + System.lineSeparator() + "Last track skipped by @user");
  ```

- [ ] **Step 2: Add a test for `WentBack`**

  In `TextChannelMusicPlayerTrackAnnouncerTest.java`, add a new test after `acceptShowsTemporarySkippedAction()`:

  ```java
  @Test
  void acceptShowsTemporaryWentBackAction() {
      AtomicReference<EmbedMessage> updated = new AtomicReference<>();
      MusicPlayerTrackAnnouncer announcer = announcer(new AtomicReference<>(), updated, new AtomicInteger(), Duration.ofSeconds(15));

      announcer.accept(trackStart("player-1"));
      announcer.accept(new MusicPlayerEvent.WentBack("player-1", "req-2", "guild-1", "@user"));

      assertThat(updated.get()).isNotNull();
      assertThat(updated.get().description()).hasValue(PLAYING_STATUS + System.lineSeparator() + "Jumped back by @user");
  }
  ```

- [ ] **Step 3: Run tests to confirm they fail**

  ```bash
  JAVA_HOME="/c/Users/dries/.jdks/openjdk-25.0.2" ./gradlew :controller:test --tests "com.bearify.controller.music.discord.TextChannelMusicPlayerTrackAnnouncerTest" -q
  ```

  Expected: FAIL — `acceptShowsTemporarySkippedAction` and `acceptShowsTemporaryWentBackAction` fail with wrong string.

- [ ] **Step 4: Update the action strings in the announcer**

  In `TextChannelMusicPlayerTrackAnnouncer.java`, in the `accept()` method switch, replace:

  Old:
  ```java
  // TODO AI i find these messages confusing, especially the  Skipped and Went back.
  //  It shows on the new track, and that is confusing. Maybe it should say something more like "Last track skipped by ..."
  case MusicPlayerEvent.Skipped skipped -> notify("Skipped by " + skipped.requesterTag());
  case MusicPlayerEvent.WentBack wentBack -> notify("Went back by " + wentBack.requesterTag());
  ```

  New:
  ```java
  case MusicPlayerEvent.Skipped skipped -> notify("Last track skipped by " + skipped.requesterTag());
  case MusicPlayerEvent.WentBack wentBack -> notify("Jumped back by " + wentBack.requesterTag());
  ```

- [ ] **Step 5: Run tests to confirm they pass**

  ```bash
  JAVA_HOME="/c/Users/dries/.jdks/openjdk-25.0.2" ./gradlew :controller:test --tests "com.bearify.controller.music.discord.TextChannelMusicPlayerTrackAnnouncerTest" -q
  ```

  Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Update the spec to reflect revised wording**

  In `docs/superpowers/specs/2026-03-26-announcer-embed-state-row-design.md`, in the Temporary Action Row section, update the two entries:

  Old:
  ```
  - `Skipped by @user`
  - `Went back by @user`
  ```

  New:
  ```
  - `Last track skipped by @user`
  - `Jumped back by @user`
  ```

  Also update the example in the Rendering section:

  Old:
  ```text
  On the air
  Skipped by @user
  ```

  New:
  ```text
  On the air
  Last track skipped by @user
  ```

  Also remove the random footer note. In the Testing Impact section, the test line:
  ```
  - action-success events render the correct `... by @user` temporary row
  ```
  remains valid as-is.

- [ ] **Step 7: Commit**

  ```bash
  git add controller/src/main/java/com/bearify/controller/music/discord/TextChannelMusicPlayerTrackAnnouncer.java \
          controller/src/test/java/com/bearify/controller/music/discord/TextChannelMusicPlayerTrackAnnouncerTest.java \
          docs/superpowers/specs/2026-03-26-announcer-embed-state-row-design.md
  git commit -m "fix confusing skipped and went-back action messages"
  ```