package com.bearify.discord.api.message;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SectionBuilderTest {

    @Test
    void buildsASectionWithTextAndThumbnailAccessory() {
        Section section = new SectionBuilder()
                .text("Now playing")
                .thumbnail("https://cdn.example/thumb.png", "Cover art")
                .build();

        assertThat(section.accessory()).contains(new Thumbnail("https://cdn.example/thumb.png", "Cover art"));
        assertThat(section.texts()).containsExactly("Now playing");
    }
}
