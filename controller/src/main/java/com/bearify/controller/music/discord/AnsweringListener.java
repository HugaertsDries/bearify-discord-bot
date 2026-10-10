package com.bearify.controller.music.discord;

import com.bearify.controller.format.BearifyEmoji;
import com.bearify.controller.music.domain.MusicPlayerEventListener;

/**
 * Answers the failures every player request can end in, with the same texts for commands and buttons.
 * Subclasses add the success answers they need, through {@link #answer(String)}.
 */
class AnsweringListener implements MusicPlayerEventListener {

    static final String NO_PLAYER_MESSAGE = BearifyEmoji.BLOCKED + " | Seems like I'm not even playing songs! If you'd like me to, have you tried the `/player play` command?";
    static final String TIMED_OUT_MESSAGE = BearifyEmoji.BLOCKED + " | The bear didn't answer in time. Try again in a moment.";
    static final String NOTHING_PLAYING_MESSAGE = BearifyEmoji.HONEY + " | Nothing's playing right now. Queue something with `/player play`!";
    static final String QUEUE_EMPTY_MESSAGE = BearifyEmoji.HONEY + " | This is where the trail ends. Try adding more using `/player play` command!";
    static final String NOTHING_TO_GO_BACK_MESSAGE = BearifyEmoji.HONEY + " | This is where the trail begins. Nothing to go back to!";

    private final Answer answer;

    AnsweringListener(Answer answer) {
        this.answer = answer;
    }

    protected void answer(String text) {
        answer.send(text);
    }

    @Override
    public void onFailed(String reason) {
        answer(NO_PLAYER_MESSAGE);
    }

    @Override
    public void onTimedOut() {
        answer(TIMED_OUT_MESSAGE);
    }

    @Override
    public void onNothingPlaying() {
        answer(NOTHING_PLAYING_MESSAGE);
    }

    @Override
    public void onNothingToAdvance() {
        answer(QUEUE_EMPTY_MESSAGE);
    }

    @Override
    public void onNothingToGoBack() {
        answer(NOTHING_TO_GO_BACK_MESSAGE);
    }

    /** Where an answer goes: the deferred command message, or an ephemeral follow-up to a button presser. */
    @FunctionalInterface
    interface Answer {
        void send(String text);
    }
}
