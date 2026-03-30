# Report Issue Command Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a controller-only `/report-issue` flow that opens a Discord modal and creates a GitHub issue from the submitted data.

**Architecture:** Extend the shared Discord abstraction with modal definitions and modal interactions, teach the JDA adapter and Spring registry layer how to dispatch those interactions, then implement a controller command plus a small GitHub issue reporting service. Keep command registration and modal routing explicit through `@Interaction(type = ...)` so existing slash commands remain backward compatible.

**Tech Stack:** Java 25, Spring Boot, JDA, JUnit 5, AssertJ, Mockito, Gradle multi-project build

---

## File Structure

- Modify: `discord/api/src/main/java/com/bearify/discord/api/interaction/CommandInteraction.java`
  - Add modal opening support to slash command interactions.
- Create: `discord/api/src/main/java/com/bearify/discord/api/interaction/InteractionType.java`
  - Shared enum for command vs modal handlers.
- Create: `discord/api/src/main/java/com/bearify/discord/api/interaction/ModalInteraction.java`
  - Runtime abstraction for submitted modal data.
- Create: `discord/api/src/main/java/com/bearify/discord/api/interaction/ModalDefinition.java`
  - Transport object for modal id, title, and fields.
- Create: `discord/api/src/main/java/com/bearify/discord/api/interaction/ModalTextInputDefinition.java`
  - Modal field model and style enum/factory methods.
- Modify: `discord/starter/src/main/java/com/bearify/discord/spring/annotation/Interaction.java`
  - Add `type()` with default `COMMAND`.
- Modify: `discord/starter/src/main/java/com/bearify/discord/spring/CommandHandler.java`
  - Keep command-only parameter resolution and reject modal misuse clearly.
- Modify: `discord/starter/src/main/java/com/bearify/discord/spring/CommandRegistry.java`
  - Register only `COMMAND` interactions.
- Create: `discord/starter/src/main/java/com/bearify/discord/spring/ModalHandler.java`
  - Invoke modal methods with `ModalInteraction`.
- Create: `discord/starter/src/main/java/com/bearify/discord/spring/ModalRegistry.java`
  - Register and dispatch modal interactions.
- Modify: `discord/starter/src/main/java/com/bearify/discord/spring/DiscordAutoConfiguration.java`
  - Create the modal registry bean and wire modal dispatch into the Discord client factory.
- Modify: `discord/api/src/main/java/com/bearify/discord/api/gateway/DiscordClientFactory.java`
  - Accept both command and modal consumers.
- Modify: `discord/jda/src/main/java/com/bearify/discord/jda/JdaDiscordClient.java`
  - Pass both handlers to the event listener and keep command registration unchanged.
- Modify: `discord/jda/src/main/java/com/bearify/discord/jda/JdaDiscordClientFactory.java`
  - Match new factory signature.
- Modify: `discord/jda/src/main/java/com/bearify/discord/jda/JdaCommandInteraction.java`
  - Implement `showModal(...)`.
- Create: `discord/jda/src/main/java/com/bearify/discord/jda/JdaModalInteraction.java`
  - Wrap `ModalInteractionEvent`.
- Modify: `discord/jda/src/main/java/com/bearify/discord/jda/JdaEventListener.java`
  - Forward both slash commands and modal submissions.
- Modify: `discord/testing/src/main/java/com/bearify/discord/testing/MockDiscordClient.java`
  - Store both command and modal handlers and allow dispatching each.
- Modify: `discord/testing/src/main/java/com/bearify/discord/testing/MockCommandInteraction.java`
  - Capture shown modal definitions.
- Create: `discord/testing/src/main/java/com/bearify/discord/testing/MockModalInteraction.java`
  - Test double for modal submissions.
- Create: `controller/src/main/java/com/bearify/controller/misc/discord/GitHubIssueReporterProperties.java`
  - Configurable owner/repo/token/enabled/labels settings.
- Create: `controller/src/main/java/com/bearify/controller/misc/discord/GitHubIssueReporter.java`
  - Port for creating GitHub issues.
- Create: `controller/src/main/java/com/bearify/controller/misc/discord/HttpGitHubIssueReporter.java`
  - `java.net.http.HttpClient` implementation using Jackson.
- Create: `controller/src/main/java/com/bearify/controller/misc/discord/GitHubIssueBodyFormatter.java`
  - Formats structured issue body plus Discord metadata.
- Create: `controller/src/main/java/com/bearify/controller/misc/discord/ReportIssueCommand.java`
  - Opens the modal and handles modal submission.
- Create: `controller/src/main/java/com/bearify/controller/misc/discord/GitHubIssueReporterConfig.java`
  - Spring beans for `HttpClient` and reporter wiring if properties are enabled.
- Create: `discord/api/src/test/java/com/bearify/discord/api/interaction/ModalDefinitionTest.java`
  - Focused API model test for modal field factories.
- Modify: `discord/starter/src/test/java/com/bearify/discord/spring/CommandRegistryTest.java`
  - Assert modal-typed interactions do not create slash command definitions.
- Create: `discord/starter/src/test/java/com/bearify/discord/spring/ModalRegistryTest.java`
  - Test modal registration, dispatch, duplicate detection, and unsupported modal handling.
- Modify: `discord/starter/src/test/java/com/bearify/discord/spring/DiscordAutoConfigurationTest.java`
  - Assert modal registry bean exists and mock client dispatches to it.
- Modify: `discord/jda/src/test/java/com/bearify/discord/jda/JdaEventListenerTest.java`
  - Add modal event coverage.
- Create: `controller/src/test/java/com/bearify/controller/misc/discord/GitHubIssueBodyFormatterTest.java`
  - Verify markdown format and metadata inclusion.
- Create: `controller/src/test/java/com/bearify/controller/misc/discord/ReportIssueCommandTest.java`
  - Verify modal definition and validation logic.
- Create: `controller/src/test/java/com/bearify/controller/misc/discord/ReportIssueIntegrationTest.java`
  - Verify wiring through Spring registries and success/failure replies.

### Task 1: Add modal primitives to `discord:api`

**Files:**
- Create: `discord/api/src/main/java/com/bearify/discord/api/interaction/InteractionType.java`
- Create: `discord/api/src/main/java/com/bearify/discord/api/interaction/ModalInteraction.java`
- Create: `discord/api/src/main/java/com/bearify/discord/api/interaction/ModalDefinition.java`
- Create: `discord/api/src/main/java/com/bearify/discord/api/interaction/ModalTextInputDefinition.java`
- Modify: `discord/api/src/main/java/com/bearify/discord/api/interaction/CommandInteraction.java`
- Test: `discord/api/src/test/java/com/bearify/discord/api/interaction/ModalDefinitionTest.java`

- [ ] **Step 1: Write the failing API model test**

```java
package com.bearify.discord.api.interaction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModalDefinitionTest {

    @Test
    void createsShortAndParagraphFieldsWithStableMetadata() {
        ModalDefinition modal = new ModalDefinition(
                "report-issue",
                "Report Issue",
                java.util.List.of(
                        ModalTextInputDefinition.shortText("title", "Title", true, 5, 100),
                        ModalTextInputDefinition.paragraph("description", "Description", true, 20, 2000)
                )
        );

        assertThat(modal.id()).isEqualTo("report-issue");
        assertThat(modal.fields()).hasSize(2);
        assertThat(modal.fields().getFirst().style()).isEqualTo(ModalTextInputDefinition.Style.SHORT);
        assertThat(modal.fields().getLast().style()).isEqualTo(ModalTextInputDefinition.Style.PARAGRAPH);
    }
}
```

- [ ] **Step 2: Run the API test to verify it fails**

Run: `.\gradlew.bat :discord:api:test --tests "com.bearify.discord.api.interaction.ModalDefinitionTest"`

Expected: FAIL with missing `ModalDefinition`, `ModalTextInputDefinition`, or `InteractionType` symbols.

- [ ] **Step 3: Add the minimal modal API types**

```java
package com.bearify.discord.api.interaction;

public enum InteractionType {
    COMMAND,
    MODAL
}
```

```java
package com.bearify.discord.api.interaction;

import java.util.Optional;

public interface ModalInteraction {

    String getModalId();

    Optional<String> getValue(String fieldId);

    ReplyBuilder reply(String message);

    default EditableMessage defer() {
        return defer(false);
    }

    EditableMessage defer(boolean ephemeral);

    default Optional<String> getGuildId() {
        return Optional.empty();
    }

    default Optional<String> getTextChannelId() {
        return Optional.empty();
    }

    default String getUserMention() {
        return "Someone";
    }
}
```

```java
package com.bearify.discord.api.interaction;

import java.util.List;

public record ModalDefinition(String id, String title, List<ModalTextInputDefinition> fields) {
    public ModalDefinition {
        fields = List.copyOf(fields);
    }
}
```

```java
package com.bearify.discord.api.interaction;

public record ModalTextInputDefinition(
        String id,
        String label,
        Style style,
        boolean required,
        Integer minLength,
        Integer maxLength
) {
    public enum Style {
        SHORT,
        PARAGRAPH
    }

    public static ModalTextInputDefinition shortText(String id, String label, boolean required, Integer minLength, Integer maxLength) {
        return new ModalTextInputDefinition(id, label, Style.SHORT, required, minLength, maxLength);
    }

    public static ModalTextInputDefinition paragraph(String id, String label, boolean required, Integer minLength, Integer maxLength) {
        return new ModalTextInputDefinition(id, label, Style.PARAGRAPH, required, minLength, maxLength);
    }
}
```

```java
package com.bearify.discord.api.interaction;

import java.util.Optional;

public interface CommandInteraction {

    default EditableMessage defer() {
        return defer(false);
    }

    EditableMessage defer(boolean ephemeral);

    ReplyBuilder reply(String message);

    void showModal(ModalDefinition modal);

    Optional<String> getOption(String name);

    String getName();

    Optional<String> getSubcommandName();

    Optional<String> getGuildId();

    Optional<String> getVoiceChannelId();

    Optional<String> getTextChannelId();

    default String getUserMention() {
        return "Someone";
    }
}
```

- [ ] **Step 4: Run the API test to verify it passes**

Run: `.\gradlew.bat :discord:api:test --tests "com.bearify.discord.api.interaction.ModalDefinitionTest"`

Expected: PASS

- [ ] **Step 5: Commit the API primitives**

```bash
git add discord/api/src/main/java/com/bearify/discord/api/interaction/CommandInteraction.java discord/api/src/main/java/com/bearify/discord/api/interaction/InteractionType.java discord/api/src/main/java/com/bearify/discord/api/interaction/ModalDefinition.java discord/api/src/main/java/com/bearify/discord/api/interaction/ModalInteraction.java discord/api/src/main/java/com/bearify/discord/api/interaction/ModalTextInputDefinition.java discord/api/src/test/java/com/bearify/discord/api/interaction/ModalDefinitionTest.java
git commit -m "feat: add discord modal interaction api"
```

### Task 2: Extend the Spring registry layer for `@Interaction(type = MODAL)`

**Files:**
- Modify: `discord/starter/src/main/java/com/bearify/discord/spring/annotation/Interaction.java`
- Modify: `discord/starter/src/main/java/com/bearify/discord/spring/CommandRegistry.java`
- Modify: `discord/starter/src/main/java/com/bearify/discord/spring/CommandHandler.java`
- Create: `discord/starter/src/main/java/com/bearify/discord/spring/ModalHandler.java`
- Create: `discord/starter/src/main/java/com/bearify/discord/spring/ModalRegistry.java`
- Modify: `discord/starter/src/main/java/com/bearify/discord/spring/DiscordAutoConfiguration.java`
- Modify: `discord/starter/src/test/java/com/bearify/discord/spring/CommandRegistryTest.java`
- Create: `discord/starter/src/test/java/com/bearify/discord/spring/ModalRegistryTest.java`
- Modify: `discord/starter/src/test/java/com/bearify/discord/spring/DiscordAutoConfigurationTest.java`

- [ ] **Step 1: Write the failing modal registry tests**

```java
package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.InteractionType;
import com.bearify.discord.api.interaction.ModalInteraction;
import com.bearify.discord.spring.annotation.Command;
import com.bearify.discord.spring.annotation.Interaction;
import com.bearify.discord.testing.MockModalInteraction;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ModalRegistryTest {

    @Command
    static class ModalController {
        boolean invoked;

        @Interaction(type = InteractionType.MODAL, value = "report-issue")
        void reportIssue(ModalInteraction interaction) {
            invoked = true;
            interaction.reply("created").ephemeral().send();
        }
    }

    @Test
    void dispatchesModalInteractionToMatchingHandler() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("modalController", ModalController.class, ModalController::new);
            context.refresh();

            ModalRegistry registry = new ModalRegistry(context);
            Method method = ModalController.class.getDeclaredMethod("reportIssue", ModalInteraction.class);
            registry.register("modalController", method.getAnnotation(Interaction.class), method);

            MockModalInteraction interaction = MockModalInteraction.forModal("report-issue").build();
            registry.handle(interaction);

            assertThat(context.getBean(ModalController.class).invoked).isTrue();
            assertThat(interaction.getReplies().getFirst().getContent()).isEqualTo("created");
        }
    }
}
```

```java
@Test
void commandRegistryIgnoresModalTypedInteractions() throws Exception {
    Method method = ModalController.class.getDeclaredMethod("reportIssue", ModalInteraction.class);

    registry.register("modalController", method.getAnnotation(Interaction.class), method);

    assertThat(registry.getDefinitions()).isEmpty();
}
```

```java
@Test
void autoConfigurationExposesModalRegistry() {
    contextRunner
            .withPropertyValues("discord.token=test-token")
            .withUserConfiguration(MockClientConfig.class, ModalController.class)
            .run(ctx -> assertThat(ctx).hasSingleBean(ModalRegistry.class));
}
```

- [ ] **Step 2: Run the starter tests to verify they fail**

Run: `.\gradlew.bat :discord:starter:test --tests "com.bearify.discord.spring.ModalRegistryTest" --tests "com.bearify.discord.spring.CommandRegistryTest" --tests "com.bearify.discord.spring.DiscordAutoConfigurationTest"`

Expected: FAIL with missing `ModalRegistry`, `ModalInteraction`, or unsupported parameter resolution.

- [ ] **Step 3: Implement explicit modal registration and dispatch**

```java
package com.bearify.discord.spring.annotation;

import com.bearify.discord.api.interaction.InteractionType;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Interaction {
    InteractionType type() default InteractionType.COMMAND;
    String value();
    String description() default "";
}
```

```java
package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.ModalInteraction;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class ModalHandler {

    private final ApplicationContext context;
    private final String name;
    private final Method method;

    ModalHandler(ApplicationContext context, String name, Method method) {
        this.context = context;
        this.name = name;
        this.method = method;
        this.method.setAccessible(true);
    }

    void invoke(ModalInteraction interaction) {
        Object target = context.getBean(name);
        Method invocable = AopUtils.selectInvocableMethod(method, target.getClass());
        try {
            invocable.invoke(target, interaction);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Modal handler threw a checked exception: " + invocable, cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not access modal handler: " + invocable, e);
        }
    }
}
```

```java
package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.InteractionType;
import com.bearify.discord.api.interaction.ModalInteraction;
import com.bearify.discord.spring.annotation.Command;
import com.bearify.discord.spring.annotation.Interaction;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ModalRegistry {

    private final Map<String, ModalHandler> handlers = new HashMap<>();
    private final ApplicationContext context;

    ModalRegistry(ApplicationContext context) {
        this.context = context;
    }

    void register(String beanName, Interaction interaction, Method method) {
        if (interaction.type() != InteractionType.MODAL) {
            return;
        }
        Command command = AnnotationUtils.findAnnotation(method.getDeclaringClass(), Command.class);
        if (command == null) {
            throw new IllegalStateException("Interaction " + interaction + " has no @Command annotation");
        }
        if (handlers.putIfAbsent(interaction.value(), new ModalHandler(context, beanName, method)) != null) {
            throw new IllegalStateException("Duplicate modal interaction '" + interaction.value() + "'");
        }
    }

    public void handle(ModalInteraction interaction) {
        ModalHandler handler = handlers.get(interaction.getModalId());
        if (handler == null) {
            interaction.reply("This modal is not supported.").ephemeral().send();
            return;
        }
        handler.invoke(interaction);
    }
}
```

```java
void register(String name, Interaction interaction, Method method) {
    if (interaction.type() != com.bearify.discord.api.interaction.InteractionType.COMMAND) {
        return;
    }
    if (context == null) {
        throw new IllegalStateException("Lazy command registration requires an ApplicationContext");
    }
    Command command = AnnotationUtils.findAnnotation(method.getDeclaringClass(), Command.class);
    register(command, interaction, method, new CommandHandler(context, name, method));
}
```

```java
@Bean
public ModalRegistry modalRegistry(ApplicationContext context) {
    ModalRegistry registry = new ModalRegistry(context);
    scanner.scan(context, Command.class, Interaction.class, registry::register);
    return registry;
}
```

```java
@Bean
public DiscordClient discordClient(DiscordClientFactory factory,
                                   CommandRegistry commandRegistry,
                                   ModalRegistry modalRegistry,
                                   DiscordProperties properties) {
    return properties.activity()
            .map(activity -> factory.create(commandRegistry.getDefinitions(), commandRegistry::handle, modalRegistry::handle, activity))
            .orElseGet(() -> factory.create(commandRegistry.getDefinitions(), commandRegistry::handle, modalRegistry::handle));
}
```

- [ ] **Step 4: Run the starter tests to verify they pass**

Run: `.\gradlew.bat :discord:starter:test --tests "com.bearify.discord.spring.ModalRegistryTest" --tests "com.bearify.discord.spring.CommandRegistryTest" --tests "com.bearify.discord.spring.DiscordAutoConfigurationTest"`

Expected: PASS

- [ ] **Step 5: Commit the registry layer**

```bash
git add discord/starter/src/main/java/com/bearify/discord/spring/annotation/Interaction.java discord/starter/src/main/java/com/bearify/discord/spring/CommandHandler.java discord/starter/src/main/java/com/bearify/discord/spring/CommandRegistry.java discord/starter/src/main/java/com/bearify/discord/spring/ModalHandler.java discord/starter/src/main/java/com/bearify/discord/spring/ModalRegistry.java discord/starter/src/main/java/com/bearify/discord/spring/DiscordAutoConfiguration.java discord/starter/src/test/java/com/bearify/discord/spring/CommandRegistryTest.java discord/starter/src/test/java/com/bearify/discord/spring/ModalRegistryTest.java discord/starter/src/test/java/com/bearify/discord/spring/DiscordAutoConfigurationTest.java
git commit -m "feat: add modal interaction registry"
```

### Task 3: Wire modal support through JDA and testing doubles

**Files:**
- Modify: `discord/api/src/main/java/com/bearify/discord/api/gateway/DiscordClientFactory.java`
- Modify: `discord/jda/src/main/java/com/bearify/discord/jda/JdaDiscordClient.java`
- Modify: `discord/jda/src/main/java/com/bearify/discord/jda/JdaDiscordClientFactory.java`
- Modify: `discord/jda/src/main/java/com/bearify/discord/jda/JdaCommandInteraction.java`
- Create: `discord/jda/src/main/java/com/bearify/discord/jda/JdaModalInteraction.java`
- Modify: `discord/jda/src/main/java/com/bearify/discord/jda/JdaEventListener.java`
- Modify: `discord/jda/src/test/java/com/bearify/discord/jda/JdaEventListenerTest.java`
- Modify: `discord/testing/src/main/java/com/bearify/discord/testing/MockDiscordClient.java`
- Modify: `discord/testing/src/main/java/com/bearify/discord/testing/MockCommandInteraction.java`
- Create: `discord/testing/src/main/java/com/bearify/discord/testing/MockModalInteraction.java`

- [ ] **Step 1: Write the failing listener and mock tests**

```java
@Test
void submitsModalHandlerInvocationToExecutorInsteadOfCallingItInline() {
    List<Runnable> captured = new ArrayList<>();
    AtomicInteger handlerCalls = new AtomicInteger(0);

    JdaEventListener listener = new JdaEventListener(
            captured::add,
            command -> {},
            modal -> handlerCalls.incrementAndGet(),
            event -> mock(CommandInteraction.class),
            event -> mock(com.bearify.discord.api.interaction.ModalInteraction.class)
    );

    listener.onModalInteraction(mock(net.dv8tion.jda.api.events.interaction.ModalInteractionEvent.class));

    assertThat(handlerCalls.get()).isZero();
    assertThat(captured).hasSize(1);
}
```

```java
@Test
void commandInteractionCapturesShownModal() {
    MockCommandInteraction interaction = MockCommandInteraction.forCommand("report-issue").build();
    ModalDefinition modal = new ModalDefinition(
            "report-issue",
            "Report Issue",
            java.util.List.of(ModalTextInputDefinition.shortText("title", "Title", true, 5, 100))
    );

    interaction.showModal(modal);

    assertThat(interaction.getShownModal()).contains(modal);
}
```

- [ ] **Step 2: Run the JDA and testing module tests to verify they fail**

Run: `.\gradlew.bat :discord:jda:test --tests "com.bearify.discord.jda.JdaEventListenerTest" :discord:starter:test --tests "com.bearify.discord.spring.DiscordAutoConfigurationTest"`

Expected: FAIL with constructor mismatch, missing modal event support, or missing mock modal capture.

- [ ] **Step 3: Implement modal dispatch in the adapter and mocks**

```java
package com.bearify.discord.api.gateway;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.ModalInteraction;
import com.bearify.discord.api.model.CommandDefinition;

import java.util.List;
import java.util.function.Consumer;

public interface DiscordClientFactory {

    DiscordClient create(List<CommandDefinition> commands,
                         Consumer<CommandInteraction> commandHandler,
                         Consumer<ModalInteraction> modalHandler);

    DiscordClient create(List<CommandDefinition> commands,
                         Consumer<CommandInteraction> commandHandler,
                         Consumer<ModalInteraction> modalHandler,
                         Activity activity);
}
```

```java
class JdaEventListener extends ListenerAdapter {

    private final Executor executor;
    private final Consumer<CommandInteraction> commandHandler;
    private final Consumer<ModalInteraction> modalHandler;
    private final Function<SlashCommandInteractionEvent, CommandInteraction> commandFactory;
    private final Function<ModalInteractionEvent, ModalInteraction> modalFactory;

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        CommandInteraction interaction = commandFactory.apply(event);
        executor.execute(() -> commandHandler.accept(interaction));
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        ModalInteraction interaction = modalFactory.apply(event);
        executor.execute(() -> modalHandler.accept(interaction));
    }
}
```

```java
@Override
public void showModal(ModalDefinition modal) {
    net.dv8tion.jda.api.interactions.modals.Modal.Builder builder =
            net.dv8tion.jda.api.interactions.modals.Modal.create(modal.id(), modal.title());
    for (ModalTextInputDefinition field : modal.fields()) {
        net.dv8tion.jda.api.interactions.components.text.TextInput input =
                net.dv8tion.jda.api.interactions.components.text.TextInput.create(
                                field.id(),
                                field.label(),
                                field.style() == ModalTextInputDefinition.Style.SHORT
                                        ? net.dv8tion.jda.api.interactions.components.text.TextInputStyle.SHORT
                                        : net.dv8tion.jda.api.interactions.components.text.TextInputStyle.PARAGRAPH)
                        .setRequired(field.required())
                        .setRequiredRange(field.minLength() == null ? 0 : field.minLength(),
                                field.maxLength() == null ? 4000 : field.maxLength())
                        .build();
        builder.addActionRow(input);
    }
    event.replyModal(builder.build()).queue();
}
```

```java
public class MockCommandInteraction implements CommandInteraction {
    private ModalDefinition shownModal;

    @Override
    public void showModal(ModalDefinition modal) {
        this.shownModal = modal;
    }

    public Optional<ModalDefinition> getShownModal() {
        return Optional.ofNullable(shownModal);
    }
}
```

```java
public class MockModalInteraction implements com.bearify.discord.api.interaction.ModalInteraction {
    private final String modalId;
    private final Map<String, String> values;
    private final String guildId;
    private final String textChannelId;
    private final String userMention;
    private final List<MockReplyBuilder> replies = new ArrayList<>();
    private MockEditableMessage deferredMessage;

    public static Builder forModal(String modalId) {
        return new Builder(modalId);
    }

    @Override
    public String getModalId() {
        return modalId;
    }

    @Override
    public Optional<String> getValue(String fieldId) {
        return Optional.ofNullable(values.get(fieldId));
    }

    @Override
    public ReplyBuilder reply(String message) {
        MockReplyBuilder reply = new MockReplyBuilder(message);
        replies.add(reply);
        return reply;
    }

    @Override
    public EditableMessage defer(boolean ephemeral) {
        this.deferredMessage = new MockEditableMessage();
        return deferredMessage;
    }
}
```

- [ ] **Step 4: Run the JDA and testing-related tests to verify they pass**

Run: `.\gradlew.bat :discord:jda:test --tests "com.bearify.discord.jda.JdaEventListenerTest" :discord:starter:test --tests "com.bearify.discord.spring.DiscordAutoConfigurationTest"`

Expected: PASS

- [ ] **Step 5: Commit the adapter and mocks**

```bash
git add discord/api/src/main/java/com/bearify/discord/api/gateway/DiscordClientFactory.java discord/jda/src/main/java/com/bearify/discord/jda/JdaDiscordClient.java discord/jda/src/main/java/com/bearify/discord/jda/JdaDiscordClientFactory.java discord/jda/src/main/java/com/bearify/discord/jda/JdaCommandInteraction.java discord/jda/src/main/java/com/bearify/discord/jda/JdaModalInteraction.java discord/jda/src/main/java/com/bearify/discord/jda/JdaEventListener.java discord/jda/src/test/java/com/bearify/discord/jda/JdaEventListenerTest.java discord/testing/src/main/java/com/bearify/discord/testing/MockDiscordClient.java discord/testing/src/main/java/com/bearify/discord/testing/MockCommandInteraction.java discord/testing/src/main/java/com/bearify/discord/testing/MockModalInteraction.java
git commit -m "feat: wire modal interactions through discord adapters"
```

### Task 4: Add GitHub issue formatting and HTTP reporting in `controller`

**Files:**
- Create: `controller/src/main/java/com/bearify/controller/misc/discord/GitHubIssueReporterProperties.java`
- Create: `controller/src/main/java/com/bearify/controller/misc/discord/GitHubIssueReporter.java`
- Create: `controller/src/main/java/com/bearify/controller/misc/discord/HttpGitHubIssueReporter.java`
- Create: `controller/src/main/java/com/bearify/controller/misc/discord/GitHubIssueBodyFormatter.java`
- Create: `controller/src/main/java/com/bearify/controller/misc/discord/GitHubIssueReporterConfig.java`
- Create: `controller/src/test/java/com/bearify/controller/misc/discord/GitHubIssueBodyFormatterTest.java`

- [ ] **Step 1: Write the failing formatter test**

```java
package com.bearify.controller.misc.discord;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubIssueBodyFormatterTest {

    @Test
    void formatsIssueBodyWithDiscordMetadataSection() {
        String body = new GitHubIssueBodyFormatter().format(new GitHubIssueBodyFormatter.Input(
                "Critical",
                "Playback froze after queueing a playlist",
                "1. Join voice\n2. Run /play\n3. Queue a playlist",
                "@dries",
                "guild-1",
                "channel-2",
                Instant.parse("2026-03-29T10:15:30Z")
        ));

        assertThat(body).contains("## Severity", "Critical");
        assertThat(body).contains("## Description", "Playback froze");
        assertThat(body).contains("## Steps to Reproduce");
        assertThat(body).contains("## Discord Metadata");
        assertThat(body).contains("@dries", "guild-1", "channel-2", "2026-03-29T10:15:30Z");
    }
}
```

- [ ] **Step 2: Run the controller formatter test to verify it fails**

Run: `.\gradlew.bat :controller:test --tests "com.bearify.controller.misc.discord.GitHubIssueBodyFormatterTest"`

Expected: FAIL with missing formatter/reporter property classes.

- [ ] **Step 3: Implement the formatting and reporting service**

```java
package com.bearify.controller.misc.discord;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties("bearify.github.issue-reporter")
public record GitHubIssueReporterProperties(
        @DefaultValue("false") boolean enabled,
        String owner,
        String repo,
        String token,
        @DefaultValue List<String> labels
) {}
```

```java
package com.bearify.controller.misc.discord;

public interface GitHubIssueReporter {

    CreatedIssue createIssue(String title, String body);

    record CreatedIssue(String url) {}
}
```

```java
package com.bearify.controller.misc.discord;

import java.time.Instant;

public class GitHubIssueBodyFormatter {

    public String format(Input input) {
        String steps = input.steps().isBlank() ? "_Not provided_" : input.steps();
        return """
                ## Severity
                %s

                ## Description
                %s

                ## Steps to Reproduce
                %s

                ## Discord Metadata
                - Reporter: %s
                - Guild ID: %s
                - Channel ID: %s
                - Submitted At: %s
                """.formatted(
                input.severity(),
                input.description(),
                steps,
                input.userMention(),
                input.guildId(),
                input.textChannelId(),
                input.submittedAt()
        );
    }

    public record Input(
            String severity,
            String description,
            String steps,
            String userMention,
            String guildId,
            String textChannelId,
            Instant submittedAt
    ) {}
}
```

```java
package com.bearify.controller.misc.discord;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
class GitHubIssueReporterConfig {

    @Bean
    HttpClient gitHubHttpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    GitHubIssueBodyFormatter gitHubIssueBodyFormatter() {
        return new GitHubIssueBodyFormatter();
    }
}
```

```java
package com.bearify.controller.misc.discord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class HttpGitHubIssueReporter implements GitHubIssueReporter {

    private static final Logger LOG = LoggerFactory.getLogger(HttpGitHubIssueReporter.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GitHubIssueReporterProperties properties;

    public HttpGitHubIssueReporter(HttpClient httpClient, ObjectMapper objectMapper, GitHubIssueReporterProperties properties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public CreatedIssue createIssue(String title, String body) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "title", title,
                    "body", body,
                    "labels", properties.labels()
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/%s/%s/issues".formatted(properties.owner(), properties.repo())))
                    .header("Authorization", "Bearer " + properties.token())
                    .header("Accept", "application/vnd.github+json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.error("GitHub issue creation failed with status {} and body {}", response.statusCode(), response.body());
                throw new IllegalStateException("GitHub issue creation failed");
            }
            String url = objectMapper.readTree(response.body()).path("html_url").asText();
            return new CreatedIssue(url);
        } catch (IOException e) {
            throw new IllegalStateException("GitHub issue creation failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub issue creation failed", e);
        }
    }
}
```

- [ ] **Step 4: Run the formatter test to verify it passes**

Run: `.\gradlew.bat :controller:test --tests "com.bearify.controller.misc.discord.GitHubIssueBodyFormatterTest"`

Expected: PASS

- [ ] **Step 5: Commit the GitHub reporter support**

```bash
git add controller/src/main/java/com/bearify/controller/misc/discord/GitHubIssueReporterProperties.java controller/src/main/java/com/bearify/controller/misc/discord/GitHubIssueReporter.java controller/src/main/java/com/bearify/controller/misc/discord/HttpGitHubIssueReporter.java controller/src/main/java/com/bearify/controller/misc/discord/GitHubIssueBodyFormatter.java controller/src/main/java/com/bearify/controller/misc/discord/GitHubIssueReporterConfig.java controller/src/test/java/com/bearify/controller/misc/discord/GitHubIssueBodyFormatterTest.java
git commit -m "feat: add github issue reporter support"
```

### Task 5: Implement `ReportIssueCommand` and its unit tests

**Files:**
- Create: `controller/src/main/java/com/bearify/controller/misc/discord/ReportIssueCommand.java`
- Create: `controller/src/test/java/com/bearify/controller/misc/discord/ReportIssueCommandTest.java`

- [ ] **Step 1: Write the failing command tests**

```java
package com.bearify.controller.misc.discord;

import com.bearify.discord.api.interaction.ModalDefinition;
import com.bearify.discord.api.interaction.ModalTextInputDefinition;
import com.bearify.discord.testing.MockCommandInteraction;
import com.bearify.discord.testing.MockModalInteraction;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ReportIssueCommandTest {

    @Test
    void opensConfiguredModalFromSlashCommand() {
        ReportIssueCommand command = new ReportIssueCommand(
                Clock.fixed(Instant.parse("2026-03-29T10:15:30Z"), ZoneOffset.UTC),
                (title, body) -> new GitHubIssueReporter.CreatedIssue("https://github.com/bearify/issues/123"),
                new GitHubIssueBodyFormatter(),
                new GitHubIssueReporterProperties(true, "bearify", "discord-bot", "token", java.util.List.of("discord"))
        );

        MockCommandInteraction interaction = MockCommandInteraction.forCommand("report-issue").build();

        command.reportIssue(interaction);

        ModalDefinition modal = interaction.getShownModal().orElseThrow();
        assertThat(modal.id()).isEqualTo("report-issue");
        assertThat(modal.fields()).extracting(ModalTextInputDefinition::id)
                .containsExactly("title", "severity", "description", "steps");
    }

    @Test
    void validatesBlankTitleBeforeCallingGithub() {
        RecordingGitHubIssueReporter reporter = new RecordingGitHubIssueReporter();
        ReportIssueCommand command = new ReportIssueCommand(
                Clock.fixed(Instant.parse("2026-03-29T10:15:30Z"), ZoneOffset.UTC),
                reporter,
                new GitHubIssueBodyFormatter(),
                new GitHubIssueReporterProperties(true, "bearify", "discord-bot", "token", java.util.List.of())
        );

        MockModalInteraction interaction = MockModalInteraction.forModal("report-issue")
                .value("title", " ")
                .value("severity", "High")
                .value("description", "Player stopped responding")
                .build();

        command.onReportIssue(interaction);

        assertThat(reporter.calls).isZero();
        assertThat(interaction.getReplies().getFirst().getContent()).contains("title");
        assertThat(interaction.getReplies().getFirst().isEphemeral()).isTrue();
    }

    static class RecordingGitHubIssueReporter implements GitHubIssueReporter {
        int calls = 0;

        @Override
        public CreatedIssue createIssue(String title, String body) {
            calls++;
            return new CreatedIssue("https://github.com/bearify/issues/123");
        }
    }
}
```

- [ ] **Step 2: Run the command tests to verify they fail**

Run: `.\gradlew.bat :controller:test --tests "com.bearify.controller.misc.discord.ReportIssueCommandTest"`

Expected: FAIL with missing command class or modal field imports.

- [ ] **Step 3: Implement the controller command**

```java
package com.bearify.controller.misc.discord;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.api.interaction.InteractionType;
import com.bearify.discord.api.interaction.ModalDefinition;
import com.bearify.discord.api.interaction.ModalInteraction;
import com.bearify.discord.api.interaction.ModalTextInputDefinition;
import com.bearify.discord.spring.annotation.Command;
import com.bearify.discord.spring.annotation.Interaction;

import java.time.Clock;

@Command
public class ReportIssueCommand {

    private final Clock clock;
    private final GitHubIssueReporter reporter;
    private final GitHubIssueBodyFormatter formatter;
    private final GitHubIssueReporterProperties properties;

    public ReportIssueCommand(Clock clock,
                              GitHubIssueReporter reporter,
                              GitHubIssueBodyFormatter formatter,
                              GitHubIssueReporterProperties properties) {
        this.clock = clock;
        this.reporter = reporter;
        this.formatter = formatter;
        this.properties = properties;
    }

    @Interaction(value = "report-issue", description = "Report an issue to GitHub")
    public void reportIssue(CommandInteraction interaction) {
        interaction.showModal(new ModalDefinition(
                "report-issue",
                "Report Issue",
                java.util.List.of(
                        ModalTextInputDefinition.shortText("title", "Title", true, 5, 100),
                        ModalTextInputDefinition.shortText("severity", "Severity", true, 3, 20),
                        ModalTextInputDefinition.paragraph("description", "Description", true, 20, 2000),
                        ModalTextInputDefinition.paragraph("steps", "Steps to Reproduce", false, 0, 2000)
                )
        ));
    }

    @Interaction(type = InteractionType.MODAL, value = "report-issue")
    public void onReportIssue(ModalInteraction interaction) {
        if (!properties.enabled()) {
            interaction.reply("Issue reporting is not available right now.").ephemeral().send();
            return;
        }

        String title = interaction.getValue("title").orElse("").trim();
        String severity = interaction.getValue("severity").orElse("").trim();
        String description = interaction.getValue("description").orElse("").trim();
        String steps = interaction.getValue("steps").orElse("").trim();

        if (title.isBlank()) {
            interaction.reply("Issue title is required.").ephemeral().send();
            return;
        }
        if (severity.isBlank()) {
            interaction.reply("Severity is required.").ephemeral().send();
            return;
        }
        if (description.isBlank()) {
            interaction.reply("Description is required.").ephemeral().send();
            return;
        }

        String body = formatter.format(new GitHubIssueBodyFormatter.Input(
                severity,
                description,
                steps,
                interaction.getUserMention(),
                interaction.getGuildId().orElse("unknown"),
                interaction.getTextChannelId().orElse("unknown"),
                clock.instant()
        ));

        GitHubIssueReporter.CreatedIssue created = reporter.createIssue(title, body);
        interaction.reply("Issue created: " + created.url()).ephemeral().send();
    }
}
```

- [ ] **Step 4: Run the command tests to verify they pass**

Run: `.\gradlew.bat :controller:test --tests "com.bearify.controller.misc.discord.ReportIssueCommandTest"`

Expected: PASS

- [ ] **Step 5: Commit the controller command**

```bash
git add controller/src/main/java/com/bearify/controller/misc/discord/ReportIssueCommand.java controller/src/test/java/com/bearify/controller/misc/discord/ReportIssueCommandTest.java
git commit -m "feat: add report issue discord command"
```

### Task 6: Add integration coverage and finish end-to-end verification

**Files:**
- Create: `controller/src/test/java/com/bearify/controller/misc/discord/ReportIssueIntegrationTest.java`
- Modify: `discord/testing/src/main/java/com/bearify/discord/testing/MockDiscordClient.java`
- Modify: `discord/starter/src/test/java/com/bearify/discord/spring/DiscordAutoConfigurationTest.java`
- Modify: `controller/src/test/java/com/bearify/controller/misc/discord/ReportIssueCommandTest.java`

- [ ] **Step 1: Write the failing integration test**

```java
package com.bearify.controller.misc.discord;

import com.bearify.controller.AbstractControllerIntegrationTest;
import com.bearify.discord.spring.CommandRegistry;
import com.bearify.discord.spring.ModalRegistry;
import com.bearify.discord.testing.MockCommandInteraction;
import com.bearify.discord.testing.MockModalInteraction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

class ReportIssueIntegrationTest extends AbstractControllerIntegrationTest {

    @Autowired CommandRegistry commandRegistry;
    @Autowired ModalRegistry modalRegistry;

    @Test
    void opensModalAndCreatesGithubIssueFromSubmission() {
        MockCommandInteraction command = MockCommandInteraction.forCommand("report-issue").build();

        commandRegistry.handle(command);

        assertThat(command.getShownModal()).isPresent();

        MockModalInteraction modal = MockModalInteraction.forModal("report-issue")
                .value("title", "Playlist queue broken")
                .value("severity", "High")
                .value("description", "Queue stalls after adding a playlist")
                .value("steps", "1. Join voice\n2. /play playlist")
                .guildId("guild-1")
                .textChannelId("channel-2")
                .userMention("@reporter")
                .build();

        modalRegistry.handle(modal);

        assertThat(modal.getReplies().getFirst().getContent()).contains("https://github.com/bearify/discord-bot/issues/321");
        assertThat(modal.getReplies().getFirst().isEphemeral()).isTrue();
    }

    @TestConfiguration
    static class StubGitHubConfig {
        @Bean
        GitHubIssueReporter gitHubIssueReporter() {
            return (title, body) -> new GitHubIssueReporter.CreatedIssue("https://github.com/bearify/discord-bot/issues/321");
        }

        @Bean
        GitHubIssueReporterProperties gitHubIssueReporterProperties() {
            return new GitHubIssueReporterProperties(true, "bearify", "discord-bot", "token", java.util.List.of("discord"));
        }
    }
}
```

- [ ] **Step 2: Run the focused integration tests to verify they fail**

Run: `.\gradlew.bat :controller:test --tests "com.bearify.controller.misc.discord.ReportIssueIntegrationTest" --tests "com.bearify.controller.misc.discord.ReportIssueCommandTest"`

Expected: FAIL until the command bean, properties bean, and modal registry are all wired into the application context.

- [ ] **Step 3: Finish wiring and unsupported-path coverage**

```java
// In MockDiscordClient
private final Consumer<ModalInteraction> modalHandler;

public void dispatchModal(ModalInteraction interaction) {
    modalHandler.accept(interaction);
}
```

```java
// In DiscordAutoConfigurationTest
MockModalInteraction interaction = MockModalInteraction.forModal("report-issue")
        .value("title", "Broken")
        .value("severity", "High")
        .value("description", "It broke")
        .build();
client.dispatchModal(interaction);
assertThat(interaction.getReplies().getFirst().isSent()).isTrue();
```

```java
// Add one more integration test
@Test
void repliesEphemeralWhenIssueReportingIsDisabled() {
    MockModalInteraction interaction = MockModalInteraction.forModal("report-issue")
            .value("title", "Broken")
            .value("severity", "High")
            .value("description", "It broke")
            .build();

    new ReportIssueCommand(clock, reporter, formatter,
            new GitHubIssueReporterProperties(false, "bearify", "discord-bot", "token", java.util.List.of()))
            .onReportIssue(interaction);

    assertThat(interaction.getReplies().getFirst().getContent()).contains("not available");
}
```

- [ ] **Step 4: Run the full verification suite**

Run:

```powershell
$env:JAVA_HOME='C:\Users\dries\.jdks\openjdk-24.0.2+12-54'
$env:Path="C:\Users\dries\.jdks\openjdk-24.0.2+12-54\bin;$env:Path"
.\gradlew.bat :discord:api:test :discord:starter:test :discord:jda:test :controller:test
```

Expected: PASS across all four modules

- [ ] **Step 5: Commit the end-to-end coverage**

```bash
git add controller/src/test/java/com/bearify/controller/misc/discord/ReportIssueIntegrationTest.java discord/testing/src/main/java/com/bearify/discord/testing/MockDiscordClient.java discord/starter/src/test/java/com/bearify/discord/spring/DiscordAutoConfigurationTest.java
git commit -m "test: cover report issue modal flow end to end"
```

## Self-Review

- Spec coverage: modal API, JDA support, Spring registration, controller command, GitHub configuration, validation, metadata formatting, and tests are all mapped to Tasks 1-6.
- Placeholder scan: every task has concrete files, sample code, commands, and expected outcomes.
- Type consistency: the plan consistently uses `@Interaction(type = InteractionType.MODAL, value = "...")`, `ModalInteraction`, `showModal(ModalDefinition)`, and `GitHubIssueReporterProperties`.
