package com.bearify.discord.api.message;

import java.util.ArrayList;
import java.util.List;

public final class SectionBuilder {
    private SectionAccessory accessory;
    private final List<String> texts = new ArrayList<>();

    public SectionBuilder accessory(SectionAccessory accessory) {
        this.accessory = accessory;
        return this;
    }

    public SectionBuilder button(InteractiveButton button) {
        this.accessory = button;
        return this;
    }

    public SectionBuilder linkButton(String url, String label) {
        this.accessory = LinkButton.of(url, label);
        return this;
    }

    public SectionBuilder text(String text) {
        texts.add(text);
        return this;
    }

    Section build() {
        return new Section(accessory, texts);
    }
}
