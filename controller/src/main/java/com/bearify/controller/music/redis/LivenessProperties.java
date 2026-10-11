package com.bearify.controller.music.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** How often the controller checks that the bears behind its player messages are still there. */
@ConfigurationProperties("bearify.liveness")
public record LivenessProperties(@DefaultValue("5s") Duration interval) {

    public LivenessProperties {
        if (!interval.isPositive()) {
            throw new IllegalArgumentException("bearify.liveness.interval must be greater than 0");
        }
    }
}
