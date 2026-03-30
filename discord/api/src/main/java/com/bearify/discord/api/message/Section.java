package com.bearify.discord.api.message;

import java.util.List;
import java.util.Optional;

public final class Section implements ContainerChild {

    private final SectionAccessory accessory;
    private final List<String> texts;

    public Section(SectionAccessory accessory, List<String> texts) {
        this.accessory = accessory;
        this.texts = List.copyOf(texts);
    }

    public Optional<SectionAccessory> accessory() {
        return Optional.ofNullable(accessory);
    }

    public List<String> texts() {
        return texts;
    }

    @Override
    public Kind kind() {
        return Kind.MEDIA_SECTION;
    }
}
