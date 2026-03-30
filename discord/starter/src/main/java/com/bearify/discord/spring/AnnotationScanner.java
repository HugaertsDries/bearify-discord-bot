package com.bearify.discord.spring;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.MethodIntrospector;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.springframework.core.MethodIntrospector.selectMethods;
import static org.springframework.core.annotation.AnnotationUtils.findAnnotation;

class AnnotationScanner {

    @FunctionalInterface
    interface Handler<A extends Annotation> {
        void handle(String bean, A annotation, Method method);
    }

    <A extends Annotation> void scan(ApplicationContext context, Class<? extends Annotation> clazzAnnotation, Class<A> methodAnnotation, Handler<A> handler) {
        var factory = ((ConfigurableApplicationContext) context).getBeanFactory();

        Arrays.stream(factory.getBeanNamesForAnnotation(clazzAnnotation))
                .map(name -> new BeanDefinition(name, factory.getType(name, false)))
                .filter(BeanDefinition::isDetermined)
                .forEach(definition -> {
                    Map<Method, A> annotatedMethods =  selectMethods(definition.type, (MethodIntrospector.MetadataLookup<A>) m -> findAnnotation(m, methodAnnotation));
                    annotatedMethods.forEach((method, annotation) -> handler.handle(definition.name, annotation, method));
        });
    }

    <A extends Annotation> void scan(ApplicationContext context,
                                     Class<? extends Annotation> firstClassAnnotation,
                                     Class<? extends Annotation> secondClassAnnotation,
                                     Class<A> methodAnnotation,
                                     Handler<A> handler) {
        scan(context, firstClassAnnotation, methodAnnotation, handler);
        scan(context, secondClassAnnotation, methodAnnotation, handler);
    }

    private record BeanDefinition(String name, Class<?> type) {
        boolean isDetermined() {
            return type != null;
        }
    }
}
