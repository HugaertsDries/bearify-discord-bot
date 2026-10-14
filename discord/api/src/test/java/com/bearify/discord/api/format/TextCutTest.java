package com.bearify.discord.api.format;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextCutTest {

    // happy path

    @Test
    void cutsTextToTheLimit() {
        assertThat(TextCut.cut("abcdef", 4)).isEqualTo("abcd");
    }

    @Test
    void keepsTextWithinTheLimit() {
        assertThat(TextCut.cut("abc", 4)).isEqualTo("abc");
    }

    // edge cases

    @Test
    void cutsBeforeAnEmojiInsteadOfSplittingIt() {
        assertThat(TextCut.cut("abc🎵def", 4)).isEqualTo("abc");
    }

    @Test
    void keepsAnEmojiThatEndsAtTheLimit() {
        assertThat(TextCut.cut("ab🎵def", 4)).isEqualTo("ab🎵");
    }

    @Test
    void cutsToNothingAtAZeroLimit() {
        assertThat(TextCut.cut("abc", 0)).isEmpty();
    }
}
