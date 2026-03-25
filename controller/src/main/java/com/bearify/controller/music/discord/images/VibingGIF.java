package com.bearify.controller.music.discord.images;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Random;

public class VibingGIF {

    private static final String URLS_LOCATION = "/data/vibing-gifs.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Random RANDOM = new Random();

    private final String url;

    public VibingGIF() {
        url = selectRandomUrl();
    }

    private static String selectRandomUrl() {
        try (InputStream stream = VibingGIF.class.getResourceAsStream(URLS_LOCATION)) {
            if (stream == null) throw new IllegalStateException("vibing-gifs.json not found");
            List<String> urls = MAPPER.readValue(stream, new TypeReference<>() {});
            return urls.get(RANDOM.nextInt(urls.size()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse vibing-gifs.json", e);
        }
    }

    public byte[] toBytes() {
        try (InputStream stream = URI.create(url).toURL().openStream()) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to fetch vibing GIF from " + url, e);
        }
    }
}
