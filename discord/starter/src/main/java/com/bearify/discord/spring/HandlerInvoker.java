package com.bearify.discord.spring;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Looks up a handler bean lazily and invokes its handler method, rethrowing runtime exceptions unwrapped.
 */
final class HandlerInvoker {

    private HandlerInvoker() {
    }

    static void invoke(ApplicationContext context, String beanName, Method method, Object... args) {
        Object target = context.getBean(beanName);
        Method invocable = AopUtils.selectInvocableMethod(method, target.getClass());
        try {
            invocable.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Handler threw a checked exception: " + invocable, cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not access handler: " + invocable, e);
        }
    }
}
