package com.maplecfo.automation.limits;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Opens a GitHub issue so a person reviews the bot's finding. Uses the GITHUB_TOKEN that GitHub Actions
 * provides to the workflow; the token is never stored in the code or the repository.
 */
public class GitHubIssueNotifier implements LimitsNotifier {

    private final String repository; // "owner/name"
    private final String token;
    private final HttpClient http = HttpClient.newHttpClient();

    public GitHubIssueNotifier(String repository, String token) {
        this.repository = repository;
        this.token = token;
    }

    /** Builds a notifier from the GitHub Actions environment, or returns null when not running there. */
    public static GitHubIssueNotifier fromEnvironment() {
        String repo = System.getenv("GITHUB_REPOSITORY");
        String token = System.getenv("GITHUB_TOKEN");
        return repo == null || token == null || token.isBlank() ? null : new GitHubIssueNotifier(repo, token);
    }

    static String body(String title, String body) {
        ObjectNode n = new ObjectMapper().createObjectNode();
        n.put("title", title);
        n.put("body", body);
        n.putArray("labels").add("limits-bot");
        return n.toString();
    }

    @Override
    public void notify(String title, String body) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repository + "/issues"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .POST(HttpRequest.BodyPublishers.ofString(body(title, body)))
                .build();
        try {
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() >= 300) {
                throw new IOException("GitHub answered HTTP " + r.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
    }
}
