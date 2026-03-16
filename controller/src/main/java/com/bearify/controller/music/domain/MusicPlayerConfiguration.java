package com.bearify.controller.music.domain;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MusicPlayerConfiguration {

    @Bean
    MusicPlayerPendingRequests pendingRequests() {
        return new MusicPlayerPendingRequests();
    }

    @Bean
    MusicPlayerEventRouter eventRouter(MusicPlayerPendingRequests pendingRequests) {
        return new MusicPlayerEventRouter(pendingRequests);
    }
}
