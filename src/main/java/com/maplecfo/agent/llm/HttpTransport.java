package com.maplecfo.agent.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Sends one HTTP POST. An interface so tests can replace the network with recorded responses. */
public interface HttpTransport {

    Response post(URI uri, Map<String, String> headers, String body) throws IOException, InterruptedException;

    record Response(int status, String body) {
    }

    static HttpTransport javaHttpClient(Duration timeout) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return (uri, headers, body) -> {
            HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(b::header);
            HttpResponse<String> r = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(r.statusCode(), r.body());
        };
    }
}
