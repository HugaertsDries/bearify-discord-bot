package com.bearify.discord.api.message;

public sealed interface ContainerChild permits TextBlock, Divider, ActionRow, Section, ImageBlock {

    Kind kind();

    enum Kind {
        TEXT,
        SEPARATOR,
        ACTION_ROW,
        MEDIA_SECTION,
        IMAGE_BLOCK
    }
}
