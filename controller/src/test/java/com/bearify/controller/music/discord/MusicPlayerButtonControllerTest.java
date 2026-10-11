package com.bearify.controller.music.discord;

import com.bearify.controller.music.domain.MusicPlayer;
import com.bearify.controller.music.domain.MusicPlayerEventListener;
import com.bearify.controller.music.domain.MusicPlayerPool;
import com.bearify.discord.api.message.TextBlock;
import com.bearify.discord.testing.MockButtonInteraction;
import com.bearify.music.player.bridge.events.MusicPlayerEvent;
import com.bearify.music.player.bridge.model.TrackMetadata;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class MusicPlayerButtonControllerTest {

    @Test
    void rejectsButtonUseWhenUserIsNotInPlayersVoiceChannel() {
        RecordingMusicPlayer player = new RecordingMusicPlayer();
        RecordingMusicPlayerPool pool = new RecordingMusicPlayerPool(Optional.empty());
        MusicPlayerButtonController handler = new MusicPlayerButtonController(pool);
        MockButtonInteraction interaction = MockButtonInteraction.forButton("player:next")
                .guildId("guild-1")
                .voiceChannelId("voice-other")
                .build();

        handler.next(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("same voice channel");
        assertThat(player.nextRequester).isNull();
    }

    @Test
    void dispatchesPausePlayButtonToTogglePause() {
        RecordingMusicPlayer player = new RecordingMusicPlayer();
        RecordingMusicPlayerPool pool = new RecordingMusicPlayerPool(Optional.of(player));
        MusicPlayerButtonController handler = new MusicPlayerButtonController(pool);
        MockButtonInteraction interaction = MockButtonInteraction.forButton("player:pause-play")
                .guildId("guild-1")
                .voiceChannelId("vc-123")
                .userMention("@dries")
                .build();

        handler.pausePlay(interaction);

        assertThat(player.togglePauseRequester).isEqualTo("@dries");
        assertThat(interaction.isAcknowledged()).isTrue();
        assertThat(interaction.getDeferredMessage()).isEmpty();
        assertThat(interaction.getReplies()).isEmpty();
    }

    @Test
    void dispatchesSeekButtonsWithDefaultThirtySeconds() {
        RecordingMusicPlayer player = new RecordingMusicPlayer();
        RecordingMusicPlayerPool pool = new RecordingMusicPlayerPool(Optional.of(player));
        MusicPlayerButtonController handler = new MusicPlayerButtonController(pool);
        MockButtonInteraction rewind = MockButtonInteraction.forButton("player:rewind")
                .guildId("guild-1")
                .voiceChannelId("vc-123")
                .userMention("@dries")
                .build();
        MockButtonInteraction forward = MockButtonInteraction.forButton("player:forward")
                .guildId("guild-1")
                .voiceChannelId("vc-123")
                .userMention("@dries")
                .build();

        handler.rewind(rewind);
        handler.forward(forward);

        assertThat(player.rewindSeek).isEqualTo(Duration.ofSeconds(30));
        assertThat(player.forwardSeek).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void dispatchesClearButtonToPlayerClear() {
        RecordingMusicPlayer player = new RecordingMusicPlayer();
        RecordingMusicPlayerPool pool = new RecordingMusicPlayerPool(Optional.of(player));
        MusicPlayerButtonController handler = new MusicPlayerButtonController(pool);
        MockButtonInteraction interaction = MockButtonInteraction.forButton("player:clear")
                .guildId("guild-1")
                .voiceChannelId("vc-123")
                .userMention("@dries")
                .build();

        handler.clear(interaction);

        assertThat(player.clearRequester).isEqualTo("@dries");
        assertThat(interaction.isAcknowledged()).isTrue();
        assertThat(interaction.getDeferredMessage()).isEmpty();
        assertThat(interaction.getReplies()).isEmpty();
    }

    @Test
    void answersEachButtonOnceWhenItsRequestTimesOut() {
        RecordingMusicPlayer player = new RecordingMusicPlayer();
        player.answerWith(MusicPlayerEventListener::onTimedOut);
        MusicPlayerButtonController handler = new MusicPlayerButtonController(new RecordingMusicPlayerPool(Optional.of(player)));
        List<Consumer<MockButtonInteraction>> buttons = List.of(
                handler::pausePlay, handler::previous, handler::rewind, handler::forward, handler::next, handler::clear);

        for (Consumer<MockButtonInteraction> button : buttons) {
            MockButtonInteraction interaction = buttonInteraction();

            button.accept(interaction);

            assertThat(interaction.getReplies()).hasSize(1);
            assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
            assertThat(interaction.getReplies().getFirst().getContent()).contains("didn't answer in time");
        }
    }

    @Test
    void repliesEphemerallyWhenNothingIsPlaying() {
        RecordingMusicPlayer player = new RecordingMusicPlayer();
        player.answerWith(MusicPlayerEventListener::onNothingPlaying);
        MockButtonInteraction interaction = buttonInteraction();

        new MusicPlayerButtonController(new RecordingMusicPlayerPool(Optional.of(player))).pausePlay(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("Nothing's playing right now");
    }

    @Test
    void repliesEphemerallyWhenForwardingALiveStream() {
        RecordingMusicPlayer player = new RecordingMusicPlayer();
        player.answerWith(MusicPlayerEventListener::onNotSeekable);
        MockButtonInteraction interaction = buttonInteraction();

        new MusicPlayerButtonController(new RecordingMusicPlayerPool(Optional.of(player))).forward(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("live stream");
    }

    @Test
    void repliesEphemerallyWhenThereIsNothingToSkipTo() {
        RecordingMusicPlayer player = new RecordingMusicPlayer();
        player.answerWith(MusicPlayerEventListener::onNothingToAdvance);
        MockButtonInteraction interaction = buttonInteraction();

        new MusicPlayerButtonController(new RecordingMusicPlayerPool(Optional.of(player))).next(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().getContent()).contains("where the trail ends");
    }

    @Test
    void sendsNoReplyWhenButtonActionSucceeds() {
        RecordingMusicPlayer player = new RecordingMusicPlayer();
        player.answerWith(MusicPlayerEventListener::onSkipped);
        MockButtonInteraction interaction = buttonInteraction();

        new MusicPlayerButtonController(new RecordingMusicPlayerPool(Optional.of(player))).next(interaction);

        assertThat(interaction.isAcknowledged()).isTrue();
        assertThat(interaction.getReplies()).isEmpty();
    }

    @Test
    void sendsTracksBeyondThePlayerAsEphemeralCard() {
        RecordingMusicPlayer player = new RecordingMusicPlayer();
        player.queue = Optional.of(new MusicPlayerEvent.QueueSnapshot("player-1", "req-1", "guild-1",
                Optional.of(new TrackMetadata("Song", "Artist", "https://example.com/song", 60_000)),
                List.of(
                        new TrackMetadata("One", "Artist", "https://example.com/1", 60_000),
                        new TrackMetadata("Two", "Artist", "https://example.com/2", 60_000),
                        new TrackMetadata("Three", "Artist", "https://example.com/3", 60_000),
                        new TrackMetadata("Four", "Artist", "https://example.com/4", 60_000))));
        MockButtonInteraction interaction = buttonInteraction();

        new MusicPlayerButtonController(new RecordingMusicPlayerPool(Optional.of(player))).queue(interaction);

        assertThat(interaction.isAcknowledged()).isTrue();
        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
        assertThat(interaction.getReplies().getFirst().getComponentMessage()).hasValueSatisfying(card ->
                assertThat(card.containers().getFirst().children())
                        .contains(new TextBlock("### Further up the trail"), new TextBlock("Four\n-# Artist • 1m 00s\n"))
                        .doesNotContain(new TextBlock("Three\n-# Artist • 1m 00s\n")));
    }

    @Test
    void answersPlainTextWhenNothingIsBeyondThePlayer() {
        RecordingMusicPlayer player = new RecordingMusicPlayer();
        player.queue = Optional.of(new MusicPlayerEvent.QueueSnapshot("player-1", "req-1", "guild-1", Optional.empty(),
                List.of(new TrackMetadata("One", "Artist", "https://example.com/1", 60_000))));
        MockButtonInteraction interaction = buttonInteraction();

        new MusicPlayerButtonController(new RecordingMusicPlayerPool(Optional.of(player))).queue(interaction);

        assertThat(interaction.getReplies().getFirst().getComponentMessage()).isEmpty();
        assertThat(interaction.getReplies().getFirst().getContent()).isEqualTo(QueueCard.NOTHING_FURTHER_MESSAGE);
    }

    @Test
    void answersFullQueueWithTimeoutWhenBearDoesNotAnswer() {
        MockButtonInteraction interaction = buttonInteraction();

        new MusicPlayerButtonController(new RecordingMusicPlayerPool(Optional.of(new RecordingMusicPlayer()))).queue(interaction);

        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().getContent()).contains("didn't answer in time");
    }

    @Test
    void rejectsFullQueueFromAnotherVoiceChannel() {
        MockButtonInteraction interaction = MockButtonInteraction.forButton("player:queue")
                .guildId("guild-1")
                .voiceChannelId("voice-other")
                .build();

        new MusicPlayerButtonController(new RecordingMusicPlayerPool(Optional.empty())).queue(interaction);

        assertThat(interaction.isAcknowledged()).isFalse();
        assertThat(interaction.getReplies()).hasSize(1);
        assertThat(interaction.getReplies().getFirst().getContent()).contains("same voice channel");
    }

    private static MockButtonInteraction buttonInteraction() {
        return MockButtonInteraction.forButton("player:any")
                .guildId("guild-1")
                .voiceChannelId("vc-123")
                .userMention("@dries")
                .build();
    }

    private static final class RecordingMusicPlayerPool implements MusicPlayerPool {
        private final Optional<MusicPlayer> player;

        private RecordingMusicPlayerPool(Optional<MusicPlayer> player) {
            this.player = player;
        }

        @Override
        public MusicPlayer acquire(String guildId, String voiceChannelId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<MusicPlayer> find(String guildId, String voiceChannelId) {
            return player;
        }

        @Override
        public boolean hasActiveSessionFor(String guildId) {
            return player.isPresent();
        }
    }

    private static final class RecordingMusicPlayer implements MusicPlayer {
        private String togglePauseRequester;
        private String previousRequester;
        private String nextRequester;
        private Duration rewindSeek;
        private Duration forwardSeek;
        private String clearRequester;
        private Consumer<MusicPlayerEventListener> answer = handler -> {};
        private Optional<MusicPlayerEvent> queue = Optional.empty();

        /** How the player answers every request, as the agent's reply would. */
        private void answerWith(Consumer<MusicPlayerEventListener> answer) {
            this.answer = answer;
        }

        @Override
        public void join(MusicPlayerEventListener handler) {
        }

        @Override
        public void stop(MusicPlayerEventListener handler) {
        }

        @Override
        public void play(com.bearify.music.player.bridge.model.TrackRequest request, MusicPlayerEventListener handler) {
        }

        @Override
        public void togglePause(String requesterTag, MusicPlayerEventListener handler) {
            togglePauseRequester = requesterTag;
            answer.accept(handler);
        }

        @Override
        public void previous(String requesterTag, MusicPlayerEventListener handler) {
            previousRequester = requesterTag;
            answer.accept(handler);
        }

        @Override
        public void next(String requesterTag, MusicPlayerEventListener handler) {
            nextRequester = requesterTag;
            answer.accept(handler);
        }

        @Override
        public void rewind(Duration seek, String requesterTag, MusicPlayerEventListener handler) {
            rewindSeek = seek;
            answer.accept(handler);
        }

        @Override
        public void forward(Duration seek, String requesterTag, MusicPlayerEventListener handler) {
            forwardSeek = seek;
            answer.accept(handler);
        }

        @Override
        public void clear(String requesterTag, MusicPlayerEventListener handler) {
            clearRequester = requesterTag;
            answer.accept(handler);
        }

        @Override
        public void setVolume(int volume, String requesterTag, MusicPlayerEventListener handler) {
            answer.accept(handler);
        }

        @Override
        public Optional<MusicPlayerEvent> queue() {
            return queue;
        }
    }
}
