package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.AutocompleteInteraction;
import com.bearify.discord.api.interaction.ButtonInteraction;
import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.Option;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.interactions.AutoCompleteCallbackAction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class JdaEventListenerTest {

    // --- HAPPY PATH ---

    @Test
    void submitsHandlerInvocationToExecutorInsteadOfCallingItInline() {
        List<Runnable> captured = new ArrayList<>();
        AtomicInteger handlerCalls = new AtomicInteger(0);
        AtomicInteger buttonCalls = new AtomicInteger(0);

        JdaEventListener listener = new JdaEventListener(
                captured::add,
                interaction -> {
                    if (interaction instanceof CommandInteraction) {
                        handlerCalls.incrementAndGet();
                    }
                    if (interaction instanceof ButtonInteraction) {
                        buttonCalls.incrementAndGet();
                    }
                },
                event -> mock(CommandInteraction.class),
                event -> mock(ButtonInteraction.class),
                event -> mock(AutocompleteInteraction.class)
        );

        listener.onSlashCommandInteraction(mock(SlashCommandInteractionEvent.class));
        listener.onButtonInteraction(mock(ButtonInteractionEvent.class));

        assertThat(handlerCalls.get()).isZero();
        assertThat(buttonCalls.get()).isZero();
        assertThat(captured).hasSize(2);

        captured.getFirst().run();
        captured.getLast().run();

        assertThat(handlerCalls.get()).isEqualTo(1);
        assertThat(buttonCalls.get()).isEqualTo(1);
    }

    @Test
    void submitsOneTaskPerEvent() {
        List<Runnable> captured = new ArrayList<>();

        JdaEventListener listener = new JdaEventListener(
                captured::add,
                interaction -> {},
                event -> mock(CommandInteraction.class),
                event -> mock(ButtonInteraction.class),
                event -> mock(AutocompleteInteraction.class)
        );

        listener.onSlashCommandInteraction(mock(SlashCommandInteractionEvent.class));
        listener.onSlashCommandInteraction(mock(SlashCommandInteractionEvent.class));
        listener.onButtonInteraction(mock(ButtonInteractionEvent.class));

        assertThat(captured).hasSize(3);
    }

    // --- CONCURRENCY ---

    @Test
    void concurrentEventsDoNotBlockEachOther() throws InterruptedException {
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch bothFinished = new CountDownLatch(2);

        JdaEventListener listener = new JdaEventListener(
                Executors.newVirtualThreadPerTaskExecutor(),
                interaction -> {
                    bothStarted.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    bothFinished.countDown();
                },
                event -> mock(CommandInteraction.class),
                event -> mock(ButtonInteraction.class),
                event -> mock(AutocompleteInteraction.class)
        );

        listener.onSlashCommandInteraction(mock(SlashCommandInteractionEvent.class));
        listener.onButtonInteraction(mock(ButtonInteractionEvent.class));

        boolean bothStartedBeforeRelease = bothStarted.await(5, TimeUnit.SECONDS);
        assertThat(bothStartedBeforeRelease)
                .as("both handlers should start concurrently without blocking each other")
                .isTrue();

        release.countDown();
        bothFinished.await(5, TimeUnit.SECONDS);
    }

    @Test
    void submitsAutocompleteEventsToExecutorInsteadOfCallingItInline() {
        List<Runnable> captured = new ArrayList<>();
        AtomicInteger autocompleteCalls = new AtomicInteger();

        JdaEventListener listener = new JdaEventListener(
                captured::add,
                interaction -> {
                    if (interaction instanceof AutocompleteInteraction) {
                        autocompleteCalls.incrementAndGet();
                    }
                },
                event -> mock(CommandInteraction.class),
                event -> mock(ButtonInteraction.class),
                event -> mock(AutocompleteInteraction.class)
        );

        listener.onCommandAutoCompleteInteraction(mock(CommandAutoCompleteInteractionEvent.class));

        assertThat(autocompleteCalls.get()).isZero();
        assertThat(captured).hasSize(1);

        captured.getFirst().run();

        assertThat(autocompleteCalls.get()).isEqualTo(1);
    }

    @Test
    void adaptsAutocompleteInteractionIdValueAndContext() {
        CommandAutoCompleteInteractionEvent event = mock(CommandAutoCompleteInteractionEvent.class);
        Guild guild = mock(Guild.class);
        Member member = mock(Member.class);
        GuildVoiceState voiceState = mock(GuildVoiceState.class);
        AudioChannelUnion voiceChannel = mock(AudioChannelUnion.class);
        MessageChannelUnion textChannel = mock(MessageChannelUnion.class);
        OptionMapping option = mock(OptionMapping.class);
        AutoCompleteCallbackAction replyAction = mock(AutoCompleteCallbackAction.class);

        when(event.getName()).thenReturn("player");
        when(event.getSubcommandName()).thenReturn("play");
        when(option.getName()).thenReturn("search");
        when(option.getAsString()).thenReturn("rick astley");
        when(option.getType()).thenReturn(net.dv8tion.jda.api.interactions.commands.OptionType.STRING);
        AutoCompleteQuery focusedOption = new AutoCompleteQuery(option);
        when(event.getFocusedOption()).thenReturn(focusedOption);
        when(event.getGuild()).thenReturn(guild);
        when(guild.getId()).thenReturn("guild-123");
        when(event.getMember()).thenReturn(member);
        when(member.getVoiceState()).thenReturn(voiceState);
        when(voiceState.inAudioChannel()).thenReturn(true);
        when(voiceState.getChannel()).thenReturn(voiceChannel);
        when(voiceChannel.getId()).thenReturn("voice-456");
        when(event.getChannel()).thenReturn(textChannel);
        when(textChannel.getId()).thenReturn("text-789");
        when(event.replyChoices(anyCollection())).thenReturn(replyAction);

        JdaAutocompleteInteraction interaction = new JdaAutocompleteInteraction(event);

        assertThat(interaction.getId()).isEqualTo("player:play:search");
        assertThat(interaction.getValue()).isEqualTo("rick astley");
        assertThat(interaction.getGuildId()).contains("guild-123");
        assertThat(interaction.getVoiceChannelId()).contains("voice-456");
        assertThat(interaction.getTextChannelId()).contains("text-789");

        interaction.reply(List.of(new Option("Lofi Mix", "lofi-mix")));

        verify(event).replyChoices(List.of(new Command.Choice("Lofi Mix", "lofi-mix")));
        verify(replyAction).queue();
    }
}
