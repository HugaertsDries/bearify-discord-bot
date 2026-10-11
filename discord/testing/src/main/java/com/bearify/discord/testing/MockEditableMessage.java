package com.bearify.discord.testing;

import com.bearify.discord.api.interaction.EditableMessage;
import com.bearify.discord.api.message.ComponentMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MockEditableMessage implements EditableMessage {

    private final Instant createdAt = Instant.now();
    private final List<String> edits = new ArrayList<>();
    private final List<ComponentMessage> componentEdits = new ArrayList<>();

    @Override
    public void edit(String message) {
        edits.add(message);
    }

    @Override
    public void edit(ComponentMessage message) {
        componentEdits.add(message);
    }

    @Override
    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<String> getEdits() {
        return List.copyOf(edits);
    }

    public Optional<String> getLastEdit() {
        return edits.isEmpty() ? Optional.empty() : Optional.of(edits.getLast());
    }

    public Optional<ComponentMessage> getLastComponentEdit() {
        return componentEdits.isEmpty() ? Optional.empty() : Optional.of(componentEdits.getLast());
    }

    public boolean hasEdits() {
        return !edits.isEmpty() || !componentEdits.isEmpty();
    }
}
