package com.bearify.discord.jda;

import com.bearify.discord.api.interaction.ButtonInteraction;
import com.bearify.discord.api.interaction.CommandInteraction;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
                event -> mock(ButtonInteraction.class)
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
                event -> mock(ButtonInteraction.class)
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
                event -> mock(ButtonInteraction.class)
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
}
