package com.bearify.discord.api.message;

import java.util.ArrayList;
import java.util.List;

public final class ActionRowBuilder {
    private final List<InteractiveButton> buttons = new ArrayList<>();

    public ActionRowBuilder primary(String id, String label) {
        buttons.add(new InteractiveButton(ButtonStyle.PRIMARY, id, label));
        return this;
    }

    public ActionRowBuilder secondary(String id, String label) {
        buttons.add(new InteractiveButton(ButtonStyle.SECONDARY, id, label));
        return this;
    }

    List<InteractiveButton> build() {
        return List.copyOf(buttons);
    }
}
