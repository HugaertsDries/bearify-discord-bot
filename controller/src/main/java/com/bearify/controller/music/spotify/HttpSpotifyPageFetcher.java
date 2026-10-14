package com.bearify.controller.music.spotify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

@Component
public class HttpSpotifyPageFetcher implements SpotifyPageFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(HttpSpotifyPageFetcher.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_REDIRECTS = 5;
    // a track page is about 300 KB and the og tags sit in its <head>
    private static final long MAX_BODY_BYTES = 1024 * 1024;

    private final HttpClient client;
    private final Predicate<URI> allowed;
    private final Duration timeout;

    public HttpSpotifyPageFetcher() {
        this(HttpSpotifyPageFetcher::isSpotify, TIMEOUT);
    }

    HttpSpotifyPageFetcher(Predicate<URI> allowed, Duration timeout) {
        // redirects are followed by hand, so a short link can only lead to another Spotify page
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(timeout)
                .build();
        this.allowed = allowed;
        this.timeout = timeout;
    }

    static boolean isSpotify(URI uri) {
        return SpotifyLinkResolver.isSpotifyLink(uri.toString());
    }

    @Override
    public Optional<String> fetch(URI uri) {
        long deadline = System.nanoTime() + timeout.toNanos();
        URI current = uri;
        try {
            for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
                if (!allowed.test(current)) {
                    LOG.warn("Refusing to fetch non-Spotify page {}", current);
                    return Optional.empty();
                }
                HttpResponse<String> response = send(current, deadline);
                int status = response.statusCode();
                if (status / 100 == 3) {
                    Optional<String> location = response.headers().firstValue("Location");
                    if (location.isEmpty()) {
                        return Optional.empty();
                    }
                    current = current.resolve(location.get());
                    continue;
                }
                if (status / 100 != 2) {
                    LOG.warn("Spotify page {} answered {}", current, status);
                    return Optional.empty();
                }
                return Optional.of(response.body());
            }
            LOG.warn("Too many redirects fetching Spotify page {}", uri);
        } catch (ExecutionException | TimeoutException | IllegalArgumentException e) {
            LOG.warn("Failed to fetch Spotify page {}", current, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }

    // HttpRequest.timeout stops at the headers; waiting on the future also bounds a stalled body
    private HttpResponse<String> send(URI uri, long deadline) throws ExecutionException, TimeoutException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(timeout)
                .GET()
                .build();
        CompletableFuture<HttpResponse<String>> response =
                client.sendAsync(request, HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(), MAX_BODY_BYTES));
        try {
            return response.get(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            response.cancel(true);
            throw e;
        }
    }
}
