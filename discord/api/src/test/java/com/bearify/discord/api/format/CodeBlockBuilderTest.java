package com.bearify.discord.api.format;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.bearify.discord.api.format.Language.*;
import static org.assertj.core.api.Assertions.assertThat;

class CodeBlockBuilderTest {

    // --- HAPPY PATH ---

    @Test
    void createsAnEmptyBlock() {
        assertThat(new CodeBlockBuilder().toString()).isEqualTo("```" + TEXT.tag() + "\n```");
    }

    @Test
    void appendsContent() {
        String result = new CodeBlockBuilder().append("hello").toString();
        assertThat(result).isEqualTo("```" + TEXT.tag() + "\nhello```");
    }

    @Test
    void insertsNewline() {
        String result = new CodeBlockBuilder().append("a").newline().append("b").toString();
        assertThat(result).isEqualTo("```" + TEXT.tag() + "\na\nb```");
    }

    @Test
    void insertsBlankLine() {
        String result = new CodeBlockBuilder().append("a").blank().append("b").toString();
        assertThat(result).isEqualTo("```" + TEXT.tag() + "\na\n\nb```");
    }

    @Test
    void usesSpecifiedLanguage() {
        assertThat(new CodeBlockBuilder(JAVA).toString()).startsWith("```" + JAVA.tag() + "\n");
        assertThat(new CodeBlockBuilder(JSON).toString()).startsWith("```" + JSON.tag() + "\n");
        assertThat(new CodeBlockBuilder(BASH).toString()).startsWith("```" + BASH.tag() + "\n");
    }

    // --- CAPABILITIES ---

    @Test
    void supportsChaining() {
        String result = new CodeBlockBuilder(BASH)
                .append("echo hello")
                .newline()
                .append("echo world")
                .toString();
        assertThat(result).isEqualTo("```" + BASH.tag() + "\necho hello\necho world```");
    }

    @Test
    void closesBlockOnEveryToStringCall() {
        CodeBlockBuilder builder = new CodeBlockBuilder().append("step 1");
        String after1 = builder.toString();
        builder.newline().append("step 2");
        String after2 = builder.toString();

        assertThat(after1).isEqualTo("```" + TEXT.tag() + "\nstep 1```");
        assertThat(after2).isEqualTo("```" + TEXT.tag() + "\nstep 1\nstep 2```");
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void definesATagForEveryLanguage(Language language) {
        assertThat(language.tag()).isNotBlank();
    }
}
