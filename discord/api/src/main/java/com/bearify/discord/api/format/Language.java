package com.bearify.discord.api.format;

public enum Language {
    TEXT("text"),
    JAVA("java"),
    JSON("json"),
    YAML("yaml"),
    PYTHON("python"),
    BASH("bash");

    private final String tag;

    Language(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }
}
