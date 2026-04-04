package com.bearify.discord.api.interaction;

import java.util.List;

public interface AutocompleteInteraction extends Interaction {

    String getId();

    String getValue();

    void reply(List<Option> choices);
}
