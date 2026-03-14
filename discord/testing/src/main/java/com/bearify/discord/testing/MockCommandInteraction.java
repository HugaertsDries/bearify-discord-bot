package com.bearify.discord.testing;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.EditableMessage;
import com.bearify.discord.api.interaction.ReplyBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MockCommandInteraction implements CommandInteraction {

    private final String name;
    private final Map<String, String> options;
    private MockEditableMessage deferredMessage;
    private boolean deferredEphemeral = false;
    private final List<MockReplyBuilder> replies = new ArrayList<>();

    private MockCommandInteraction(String name, Map<String, String> options) {
        this.name = name;
        this.options = Map.copyOf(options);
    }

    @Override
    public EditableMessage defer() {
        this.deferredMessage = new MockEditableMessage();
        this.deferredEphemeral = true;
        return deferredMessage;
    }

    @Override
    public ReplyBuilder reply(String message) {
        MockReplyBuilder reply = new MockReplyBuilder(message);
        replies.add(reply);
        return reply;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Optional<String> getOption(String name) {
        return Optional.ofNullable(options.get(name));
    }

    public Optional<MockEditableMessage> getDeferredMessage() {
        return Optional.ofNullable(deferredMessage);
    }

    public boolean isDeferredEphemeral() {
        return deferredEphemeral;
    }

    public List<MockReplyBuilder> getReplies() {
        return List.copyOf(replies);
    }

    public static Builder forCommand(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private final Map<String, String> options = new HashMap<>();

        private Builder(String name) {
            this.name = name;
        }

        public Builder option(String key, String value) {
            options.put(key, value);
            return this;
        }

        public MockCommandInteraction build() {
            return new MockCommandInteraction(name, options);
        }
    }
}
