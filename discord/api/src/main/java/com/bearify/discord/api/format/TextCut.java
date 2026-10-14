package com.bearify.discord.api.format;

/** Cuts text to a length without keeping half of an emoji. */
public final class TextCut {

    private TextCut() {
    }

    /** The first {@code limit} characters of the text, one fewer when the cut would split a surrogate pair. */
    public static String cut(String text, int limit) {
        if (text.length() <= limit) {
            return text;
        }
        if (limit <= 0) {
            return "";
        }
        int end = Character.isHighSurrogate(text.charAt(limit - 1)) ? limit - 1 : limit;
        return text.substring(0, end);
    }
}
