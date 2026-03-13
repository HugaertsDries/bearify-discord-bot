package com.bearify.discord.spring;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;

class AnnotationScanner {

    @FunctionalInterface
    interface Handler<A extends Annotation> {
        void handle(A annotation, Object bean, Method method);
    }

    <A extends Annotation> void scan(
            ApplicationContext context,
            Class<? extends Annotation> beanAnnotation,
            Class<A> methodAnnotation,
            Handler<A> handler
    ) {
        context.getBeansWithAnnotation(beanAnnotation).values().forEach(bean -> {
            Class<?> targetClass = AopUtils.getTargetClass(bean);

            Map<Method, A> annotatedMethods = MethodIntrospector.selectMethods(
                    targetClass,
                    (MethodIntrospector.MetadataLookup<A>) method ->
                            AnnotationUtils.findAnnotation(method, methodAnnotation));

            annotatedMethods.forEach((method, annotation) ->
                    handler.handle(annotation, bean, method));
        });
    }
}
