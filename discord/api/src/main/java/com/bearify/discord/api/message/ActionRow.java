package com.bearify.discord.api.message;

import java.util.List;

public record ActionRow(List<InteractiveButton> buttons) implements ContainerChild {
    public ActionRow {
        buttons = List.copyOf(buttons);
    }

    @Override
    public Kind kind() {
        return Kind.ACTION_ROW;
    }
}
