package dev.j8a.gitlab;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GitLabClientTest {

    private static final String TOKEN = "test-token";
    private static final String PROJECT_PATH = "group%2Fproject";

    private HttpServer server;
    private String baseUrl;

    @Before
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void readsProjectAndBranches() throws Exception {
        server.createContext("/api/v4/", exchange -> {
            assertEquals("Bearer " + TOKEN,
                    exchange.getRequestHeaders().getFirst("Authorization"));
            String rawPath = exchange.getRequestURI().getRawPath();
            if (rawPath.equals("/api/v4/projects/" + PROJECT_PATH)) {
                respond(exchange, 200, """
                        {
                          "id": 42,
                          "path_with_namespace": "group/project",
                          "default_branch": "main",
                          "web_url": "https://gitlab.com/group/project"
                        }
                        """);
            } else if (rawPath.equals("/api/v4/projects/" + PROJECT_PATH
                    + "/repository/branches")) {
                respond(exchange, 200, """
                        [
                          {"name": "main"},
                          {"name": "feature/login"}
                        ]
                        """);
            } else {
                respond(exchange, 404, "{\"message\":\"not found\"}");
            }
        });

        GitLabClient client = new GitLabClient(baseUrl, TOKEN);

        GitLabProject project = client.getProject(PROJECT_PATH);
        assertEquals("42", project.getId());
        assertEquals("group/project", project.getPathWithNamespace());
        assertEquals("main", project.getDefaultBranch());

        List<String> branches = client.getBranches(PROJECT_PATH);
        assertEquals(List.of("main", "feature/login"), branches);
    }

    @Test
    public void createsMergeRequest() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();

        server.createContext("/api/v4/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            respond(exchange, 201, """
                    {
                      "iid": 7,
                      "web_url": "https://gitlab.com/group/project/-/merge_requests/7",
                      "title": "Draft: Add login",
                      "state": "opened"
                    }
                    """);
        });

        GitLabClient client = new GitLabClient(baseUrl, TOKEN);
        MergeRequestRequest request = new MergeRequestRequest(
                "feature/login", "main", "Add login", "Adds a login screen.",
                true, true, true);

        MergeRequestResult result = client.createMergeRequest(PROJECT_PATH, request);

        assertEquals(7, result.getIid());
        assertEquals("https://gitlab.com/group/project/-/merge_requests/7",
                result.getWebUrl());
        assertEquals("opened", result.getState());
        assertTrue(contentType.get().startsWith("application/json"));

        JSONObject json = (JSONObject) new JSONParser().parse(requestBody.get());
        assertEquals("feature/login", json.get("source_branch"));
        assertEquals("main", json.get("target_branch"));
        assertEquals("Draft: Add login", json.get("title"));
        assertEquals("Adds a login screen.", json.get("description"));
        assertEquals(Boolean.TRUE, json.get("remove_source_branch"));
        assertEquals(Boolean.TRUE, json.get("squash"));
    }

    @Test
    public void reportsStructuredGitLabError() throws Exception {
        server.createContext("/api/v4/", exchange -> respond(exchange, 400, """
                {"message":{"source_branch":["can't be blank"]}}
                """));

        GitLabClient client = new GitLabClient(baseUrl, TOKEN);
        MergeRequestRequest request = new MergeRequestRequest(
                "feature/login", "main", "Add login", "",
                false, false, false);

        GitLabClientException exception = assertThrows(GitLabClientException.class,
                () -> client.createMergeRequest(PROJECT_PATH, request));

        assertEquals(400, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("source_branch"));
        assertTrue(exception.getMessage().contains("can't be blank"));
    }

    @Test
    public void reportsSimpleGitLabError() throws Exception {
        server.createContext("/api/v4/", exchange -> respond(exchange, 401,
                "{\"message\":\"401 Unauthorized\"}"));

        GitLabClient client = new GitLabClient(baseUrl, TOKEN);

        GitLabClientException exception = assertThrows(GitLabClientException.class,
                () -> client.getProject(PROJECT_PATH));

        assertEquals(401, exception.getStatusCode());
        assertEquals("401 Unauthorized", exception.getMessage());
    }

    private static void respond(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
