package com.bearify.music.player.agent;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Application.class);
        app.addInitializers(new DotenvInitializer());
        app.run(args);
    }

    static class DotenvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            Map<String, Object> props = new HashMap<>();
            dotenv.entries().forEach(e -> props.put(e.getKey(), e.getValue()));
            ctx.getEnvironment().getPropertySources()
                    .addLast(new MapPropertySource("dotenv", props));
        }
    }
}
