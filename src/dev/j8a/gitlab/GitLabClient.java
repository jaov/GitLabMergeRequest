package dev.j8a.gitlab;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Minimal GitLab REST API v4 client used by the merge request action.
 */
public final class GitLabClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient httpClient;
    private final String apiBaseUrl;
    private final String token;

    public GitLabClient(String baseUrl, String token) {
        this(baseUrl, token, HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    GitLabClient(String baseUrl, String token, HttpClient httpClient) {
        this.apiBaseUrl = stripTrailingSlash(baseUrl) + "/api/v4";
        this.token = token;
        this.httpClient = httpClient;
    }

    public GitLabProject getProject(String encodedProjectPath) throws IOException {
        JSONObject json = getObject("/projects/" + encodedProjectPath);
        return new GitLabProject(
                asString(json.get("id")),
                asString(json.get("path_with_namespace")),
                asString(json.get("default_branch")),
                asString(json.get("web_url")));
    }

    public List<String> getBranches(String encodedProjectPath) throws IOException {
        JSONArray array = getArray("/projects/" + encodedProjectPath
                + "/repository/branches?per_page=100");
        List<String> branches = new ArrayList<>();
        for (Object item : array) {
            if (item instanceof JSONObject object) {
                String name = asString(object.get("name"));
                if (name != null && !name.isBlank()) {
                    branches.add(name);
                }
            }
        }
        return branches;
    }

    public MergeRequestResult createMergeRequest(String encodedProjectPath,
            MergeRequestRequest request) throws IOException {
        String body = request.toJson().toJSONString();
        HttpRequest httpRequest = requestBuilder(
                "/projects/" + encodedProjectPath + "/merge_requests")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = send(httpRequest);
        ensureSuccess(response);

        JSONObject json = parseObject(response.body());
        return new MergeRequestResult(
                asLong(json.get("iid")),
                asString(json.get("web_url")),
                asString(json.get("title")),
                asString(json.get("state")));
    }

    /**
     * Calls {@code GET /user}; useful for a Test Connection button.
     */
    public String getCurrentUsername() throws IOException {
        JSONObject json = getObject("/user");
        return asString(json.get("username"));
    }

    private JSONObject getObject(String path) throws IOException {
        HttpResponse<String> response = send(authenticationBuilder(path).GET().build());
        ensureSuccess(response);
        return parseObject(response.body());
    }

    private JSONArray getArray(String path) throws IOException {
        HttpResponse<String> response = send(authenticationBuilder(path).GET().build());
        ensureSuccess(response);
        return parseArray(response.body());
    }

    private HttpRequest.Builder authenticationBuilder(String path) {
        return requestBuilder(path)
                .header("Accept", "application/json");
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder(uri(path))
                .timeout(REQUEST_TIMEOUT)
                .header("PRIVATE-TOKEN", token);
    }

    private URI uri(String path) {
        return URI.create(apiBaseUrl + path);
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException {
        try {
            return httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("GitLab request interrupted", ex);
        }
    }

    private static void ensureSuccess(HttpResponse<String> response)
            throws GitLabClientException {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }
        String body = response.body();
        throw new GitLabClientException(status, extractMessage(body, status), body);
    }

    private static JSONObject parseObject(String body) throws IOException {
        Object parsed = parse(body);
        if (parsed instanceof JSONObject object) {
            return object;
        }
        throw new IOException("Unexpected GitLab response: " + body);
    }

    private static JSONArray parseArray(String body) throws IOException {
        Object parsed = parse(body);
        if (parsed instanceof JSONArray array) {
            return array;
        }
        throw new IOException("Unexpected GitLab response: " + body);
    }

    private static Object parse(String body) throws IOException {
        try {
            return new JSONParser().parse(body);
        } catch (ParseException ex) {
            throw new IOException("Could not parse GitLab response", ex);
        }
    }

    private static String extractMessage(String body, int status) {
        String fallback = "GitLab API request failed with HTTP " + status;
        if (body == null || body.isBlank()) {
            return fallback;
        }
        try {
            Object parsed = new JSONParser().parse(body);
            if (parsed instanceof JSONObject object) {
                Object message = object.get("message");
                if (message != null) {
                    return messageToString(message);
                }
                Object error = object.get("error");
                if (error != null) {
                    return String.valueOf(error);
                }
            }
        } catch (ParseException ex) {
            // Fall through to the raw body.
        }
        return fallback + ": " + body;
    }

    private static String messageToString(Object message) {
        if (message instanceof String text) {
            return text;
        }
        if (message instanceof JSONObject object) {
            StringBuilder result = new StringBuilder();
            for (Object key : object.keySet()) {
                if (result.length() > 0) {
                    result.append("; ");
                }
                result.append(key).append(": ")
                        .append(valueToString(object.get(key)));
            }
            return result.toString();
        }
        if (message instanceof JSONArray array) {
            StringBuilder result = new StringBuilder();
            for (Object item : array) {
                if (result.length() > 0) {
                    result.append("; ");
                }
                result.append(valueToString(item));
            }
            return result.toString();
        }
        return String.valueOf(message);
    }

    private static String valueToString(Object value) {
        if (value instanceof JSONArray array) {
            StringBuilder result = new StringBuilder();
            for (Object item : array) {
                if (result.length() > 0) {
                    result.append(", ");
                }
                result.append(String.valueOf(item));
            }
            return result.toString();
        }
        return String.valueOf(value);
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
