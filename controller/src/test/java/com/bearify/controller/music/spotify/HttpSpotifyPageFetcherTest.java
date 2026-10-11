package com.bearify.controller.music.spotify;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HttpSpotifyPageFetcherTest {

    private static final String PAGE = "<meta property=\"og:title\" content=\"Never Gonna Give You Up\"/>";
    private static final String FORBIDDEN_PATH = "/forbidden";
    private static final Duration TIMEOUT = Duration.ofMillis(500);

    private HttpServer server;
    private HttpSpotifyPageFetcher fetcher;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        // stands in for the Spotify-host rule, which a plain-HTTP localhost server can't satisfy
        fetcher = new HttpSpotifyPageFetcher(uri -> !uri.getPath().startsWith(FORBIDDEN_PATH), TIMEOUT);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    // --- HAPPY PATH ---

    @Test
    void fetchesPageBody() {
        serve("/track", ok(PAGE));

        assertThat(fetcher.fetch(uri("/track"))).contains(PAGE);
    }

    @Test
    void followsRelativeRedirect() {
        serve("/short", redirect("/track"));
        serve("/track", ok(PAGE));

        assertThat(fetcher.fetch(uri("/short"))).contains(PAGE);
    }

    @Test
    void acceptsOnlyHttpsSpotifyHosts() {
        assertThat(HttpSpotifyPageFetcher.isSpotify(URI.create("https://open.spotify.com/track/abc"))).isTrue();
        assertThat(HttpSpotifyPageFetcher.isSpotify(URI.create("https://spotify.link/abc"))).isTrue();
        assertThat(HttpSpotifyPageFetcher.isSpotify(URI.create("http://open.spotify.com/track/abc"))).isFalse();
        assertThat(HttpSpotifyPageFetcher.isSpotify(URI.create("https://open.spotify.com.evil.example/track"))).isFalse();
        assertThat(HttpSpotifyPageFetcher.isSpotify(URI.create("https://169.254.169.254/latest"))).isFalse();
    }

    // --- EDGE CASES ---

    @Test
    void refusesRedirectOutsideAllowedHosts() {
        AtomicInteger forbiddenHits = new AtomicInteger();
        serve("/short", redirect(FORBIDDEN_PATH));
        serve(FORBIDDEN_PATH, exchange -> {
            forbiddenHits.incrementAndGet();
            ok(PAGE).handle(exchange);
        });

        assertThat(fetcher.fetch(uri("/short"))).isEmpty();
        assertThat(forbiddenHits).hasValue(0);
    }

    @Test
    void givesUpAfterFiveRedirects() {
        AtomicInteger hits = new AtomicInteger();
        serve("/loop", exchange -> {
            hits.incrementAndGet();
            redirect("/loop").handle(exchange);
        });

        assertThat(fetcher.fetch(uri("/loop"))).isEmpty();
        assertThat(hits).hasValue(6);
    }

    @Test
    void rejectsBodyLargerThanOneMegabyte() {
        serve("/huge", ok("x".repeat(2 * 1024 * 1024)));

        assertThat(fetcher.fetch(uri("/huge"))).isEmpty();
    }

    @Test
    void givesUpWhenBodyStalls() {
        serve("/stall", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            OutputStream body = exchange.getResponseBody();
            body.write("<html>".getBytes(StandardCharsets.UTF_8));
            body.flush();
            try {
                Thread.sleep(Duration.ofSeconds(10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        long start = System.nanoTime();
        Optional<String> page = fetcher.fetch(uri("/stall"));

        assertThat(page).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
    }

    // --- HELPERS ---

    private void serve(String path, HttpHandler handler) {
        server.createContext(path, handler);
    }

    private URI uri(String path) {
        return URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + path);
    }

    private static HttpHandler ok(String body) {
        return exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        };
    }

    private static HttpHandler redirect(String location) {
        return exchange -> {
            exchange.getResponseHeaders().add("Location", location);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        };
    }
}
