package com.bearify.discord.spring;

import com.bearify.discord.api.interaction.CommandInteraction;
import com.bearify.discord.spring.annotation.Option;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Holds a reference to a bean + method that handles a specific command.
 * Resolves method parameters annotated with {@link Option} from the interaction.
 */
class CommandHandler {

    private static final Map<Class<?>, Function<String, Object>> CONVERTERS = new HashMap<>();
    private static final Map<Class<?>, Object> PRIMITIVE_DEFAULTS = new HashMap<>();

    static {
        CONVERTERS.put(String.class, s -> s);
        CONVERTERS.put(int.class, Integer::valueOf);
        CONVERTERS.put(Integer.class, Integer::valueOf);
        CONVERTERS.put(long.class, Long::valueOf);
        CONVERTERS.put(Long.class, Long::valueOf);
        CONVERTERS.put(boolean.class, Boolean::valueOf);
        CONVERTERS.put(Boolean.class, Boolean::valueOf);

        PRIMITIVE_DEFAULTS.put(int.class, 0);
        PRIMITIVE_DEFAULTS.put(long.class, 0L);
        PRIMITIVE_DEFAULTS.put(boolean.class, false);
    }

    private final ApplicationContext context;
    private final String name;
    private final Method method;
    private final List<Function<CommandInteraction, Object>> resolvers;

    CommandHandler(ApplicationContext context, String name, Method method) {
        this.context = context;
        this.name = name;
        this.method = method;
        this.method.setAccessible(true);
        this.resolvers = resolvers(method.getParameters());
    }

    void invoke(CommandInteraction interaction) {
        Object target = context.getBean(name);
        Method invocable = AopUtils.selectInvocableMethod(method, target.getClass());
        try {
            Object[] args = new Object[resolvers.size()];
            for (int i = 0; i < resolvers.size(); i++) {
                args[i] = resolvers.get(i).apply(interaction);
            }
            invocable.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Command handler threw a checked exception: " + invocable, cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not access command handler: " + invocable, e);
        }
    }

    private List<Function<CommandInteraction, Object>> resolvers(Parameter[] params) {
        return Arrays.stream(params)
                .map(param -> {
                    if (CommandInteraction.class.isAssignableFrom(param.getType()))
                        return (Function<CommandInteraction, Object>) interaction -> interaction;
                    if (param.isAnnotationPresent(Option.class))
                        return resolver(param);
                    throw new IllegalStateException(
                            "Cannot resolve parameter '" + param.getName() + "' in " + method +
                            " — add @Option or use CommandInteraction");
                })
                .toList();
    }

    private Function<CommandInteraction, Object> resolver(Parameter param) {
        Option option = param.getAnnotation(Option.class);
        Class<?> type = param.getType();

        Function<String, Object> converter = CONVERTERS.get(type);
        if (converter == null) {
            throw new IllegalStateException(
                    "Unsupported @Option parameter type '" + type.getName() + "' in " + method);
        }

        Object defaultValue = option.defaultValue().isEmpty() ? PRIMITIVE_DEFAULTS.get(type) : converter.apply(option.defaultValue());

        return interaction -> interaction.getOption(option.name())
                .map(converter)
                .orElse(defaultValue);
    }
}
