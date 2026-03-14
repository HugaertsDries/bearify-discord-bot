package com.bearify.discord.api.format;

import static com.bearify.discord.api.format.Language.TEXT;

/**
 * Builds a Discord code block, ensuring the content is always wrapped in
 * triple backticks. {@link #toString()} can be called at any point to get
 * a valid, closeable code block suitable for edits or replies.
 */
public class CodeBlockBuilder {

    private final Language language;
    private final StringBuilder content = new StringBuilder();

    public CodeBlockBuilder() {
        this(TEXT);
    }

    public CodeBlockBuilder(Language language) {
        this.language = language;
    }

    public CodeBlockBuilder append(String text) {
        content.append(text);
        return this;
    }

    public CodeBlockBuilder newline() {
        content.append("\n");
        return this;
    }

    public CodeBlockBuilder blank() {
        content.append("\n\n");
        return this;
    }

    @Override
    public String toString() {
        return "```" + language.tag() + "\n" + content + "```";
    }
}
