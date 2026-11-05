package dev.j8a.gitlab;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed Git remote URL with the GitLab web and API locations derived from it.
 *
 * <p>Supports HTTPS, HTTP, SSH, git:// and scp-like remotes such as
 * {@code git@gitlab.com:group/project.git}. For SSH and git:// remotes the
 * web base URL defaults to HTTPS because the SSH port is not the web port.</p>
 */
public final class GitRemote {

    private static final Pattern SCP_LIKE =
            Pattern.compile("^(?:(?<user>[^/@:]+)@)?(?<host>[^/:]+):(?<path>.+)$");

    private final String remoteUrl;
    private final String scheme;
    private final String host;
    private final int webPort;
    private final String projectPath;
    private final String baseUrl;

    private GitRemote(String remoteUrl, String scheme, String host, int webPort,
            String projectPath, String baseUrl) {
        this.remoteUrl = remoteUrl;
        this.scheme = scheme;
        this.host = host;
        this.webPort = webPort;
        this.projectPath = projectPath;
        this.baseUrl = baseUrl;
    }

    public static GitRemote parse(String remoteUrl) {
        Objects.requireNonNull(remoteUrl, "remoteUrl");
        String trimmed = remoteUrl.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Git remote URL is empty");
        }

        URI uri;
        try {
            uri = new URI(toUriSyntax(trimmed));
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid Git remote URL: " + remoteUrl, ex);
        }

        String remoteScheme = uri.getScheme();
        if (remoteScheme == null || remoteScheme.isEmpty()) {
            throw new IllegalArgumentException("Git remote URL has no scheme: " + remoteUrl);
        }
        remoteScheme = remoteScheme.toLowerCase(Locale.ROOT);

        String remoteHost = uri.getHost();
        if (remoteHost == null || remoteHost.isEmpty()) {
            throw new IllegalArgumentException("Git remote URL has no host: " + remoteUrl);
        }

        String path = uri.getPath();
        if (path == null) {
            path = "";
        }
        path = stripLeadingSlash(path);
        path = stripTrailingSlash(path);
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - ".git".length());
        }
        path = stripTrailingSlash(path);
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Git remote URL has no project path: " + remoteUrl);
        }

        boolean webSchemeFromRemote = "http".equals(remoteScheme) || "https".equals(remoteScheme);
        String webScheme = webSchemeFromRemote ? remoteScheme : "https";
        int port = webSchemeFromRemote ? uri.getPort() : -1;
        String base = webScheme + "://" + remoteHost + (port > 0 ? ":" + port : "");

        return new GitRemote(trimmed, remoteScheme, remoteHost, port, path, base);
    }

    private static String toUriSyntax(String remoteUrl) {
        if (remoteUrl.contains("://")) {
            return remoteUrl;
        }

        Matcher matcher = SCP_LIKE.matcher(remoteUrl);
        if (!matcher.matches()) {
            return remoteUrl;
        }

        String user = matcher.group("user");
        String host = matcher.group("host");
        String path = matcher.group("path");
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return "ssh://" + (user == null ? "" : user + "@") + host + path;
    }

    private static String stripLeadingSlash(String value) {
        int index = 0;
        while (index < value.length() && value.charAt(index) == '/') {
            index++;
        }
        return value.substring(index);
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public String getScheme() {
        return scheme;
    }

    public String getHost() {
        return host;
    }

    /**
     * Web port when the remote itself is HTTP(S), otherwise {@code -1}.
     */
    public int getWebPort() {
        return webPort;
    }

    /**
     * GitLab project path, for example {@code group/subgroup/project}.
     */
    public String getProjectPath() {
        return projectPath;
    }

    /**
     * Base web URL, for example {@code https://gitlab.com}.
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiUrl() {
        return baseUrl + "/api/v4";
    }

    public String getWebUrl() {
        return baseUrl + "/" + encodePathSegments(projectPath);
    }

    /**
     * URL-encoded project path suitable for the GitLab API {@code :id} segment.
     * The slashes are encoded as {@code %2F}.
     */
    public String getEncodedProjectPath() {
        return URLEncoder.encode(projectPath, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static String encodePathSegments(String path) {
        StringBuilder result = new StringBuilder(path.length() + 8);
        String[] segments = path.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                result.append('/');
            }
            result.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8)
                    .replace("+", "%20"));
        }
        return result.toString();
    }

    @Override
    public String toString() {
        return "GitRemote{" + remoteUrl + " -> " + baseUrl + "/" + projectPath + '}';
    }
}
