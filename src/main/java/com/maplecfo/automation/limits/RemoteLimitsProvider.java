package com.maplecfo.automation.limits;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * F15: downloads the latest contribution_limits.json, published in the project's GitHub repository
 * after the weekly bot's pull request is reviewed and merged. Only the raw text is returned here;
 * {@link LimitsService} parses and validates it before use.
 */
public class RemoteLimitsProvider {

    /** Fetches the text at a URL (a lambda in tests). */
    @FunctionalInterface
    public interface Fetcher {
        String get(URI uri) throws IOException;
    }

    private final URI url;
    private final Fetcher fetcher;

    public RemoteLimitsProvider(URI url, Fetcher fetcher) {
        this.url = url;
        this.fetcher = fetcher;
    }

    public static RemoteLimitsProvider overHttp(URI url) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        return new RemoteLimitsProvider(url, uri -> {
            try {
                HttpResponse<String> r = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() != 200) {
                    throw new IOException("HTTP " + r.statusCode());
                }
                return r.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            }
        });
    }

    public String fetch() throws IOException {
        return fetcher.get(url);
    }

    public URI url() {
        return url;
    }
}
