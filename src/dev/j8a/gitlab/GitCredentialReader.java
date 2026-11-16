package dev.j8a.gitlab;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.netbeans.api.keyring.Keyring;
import org.netbeans.libs.git.GitURI;
import org.openide.util.NbPreferences;

/**
 * Looks for an existing GitLab credential before asking the user for a token.
 *
 * <p>The primary source is the Git credential helper, which covers
 * {@code git credential-store}, Git Credential Manager, libsecret, and tools
 * such as {@code glab}. The fallback reads the NetBeans Git module's saved
 * HTTPS password from the NetBeans keyring.</p>
 */
final class GitCredentialReader {

    enum Source {
        GIT_HELPER,
        NETBEANS_KEYRING
    }

    record Credential(String username, String token, Source source) {
    }

    private record StoredConnection(String guriString, String user,
            boolean saveCredentials) {
    }

    private static final Logger LOG = Logger.getLogger(
            GitCredentialReader.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String NETBEANS_GIT_PREFERENCES = "org/netbeans/modules/git";
    private static final String GIT_PASSWORD_KEY = "guri_password";

    private GitCredentialReader() {
    }

    static Credential read(GitRemote remote) {
        List<Credential> candidates = readCandidates(remote);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    static Credential read(GitRemote remote, String gitCommand) {
        List<Credential> candidates = readCandidates(remote, gitCommand);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * Returns every credential source in priority order. The caller can try
     * the next one if the API rejects the first token.
     */
    static List<Credential> readCandidates(GitRemote remote) {
        return readCandidates(remote, "git");
    }

    static List<Credential> readCandidates(GitRemote remote, String gitCommand) {
        List<Credential> candidates = new ArrayList<>();
        Credential helperCredential = readFromGitHelper(remote, gitCommand);
        if (helperCredential != null) {
            LOG.fine("Git credential helper returned a credential");
            candidates.add(helperCredential);
        } else {
            LOG.fine("Git credential helper returned no credential");
        }
        Credential keyringCredential = readFromNetBeansKeyring(remote);
        if (keyringCredential != null
                && !containsToken(candidates, keyringCredential.token())) {
            LOG.fine("NetBeans Git keyring returned a credential");
            candidates.add(keyringCredential);
        } else if (keyringCredential == null) {
            LOG.fine("NetBeans Git keyring returned no credential");
        }
        return candidates;
    }

    private static boolean containsToken(List<Credential> candidates, String token) {
        for (Credential candidate : candidates) {
            if (candidate.token().equals(token)) {
                return true;
            }
        }
        return false;
    }

    private static Credential readFromGitHelper(GitRemote remote,
            String gitCommand) {
        String url = webUrlWithGitSuffix(remote);
        if (url == null) {
            return null;
        }

        LOG.log(Level.FINE, "Reading Git credential for {0}", url);
        ProcessBuilder builder = new ProcessBuilder(gitCommand,
                "credential", "fill");
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("GCM_INTERACTIVE", "never");

        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            LOG.log(Level.FINE, "Cannot start git credential helper", ex);
            return null;
        }

        try {
            try (OutputStream input = process.getOutputStream()) {
                input.write(("url=" + url + "\n\n")
                        .getBytes(StandardCharsets.UTF_8));
            }

            if (!process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                LOG.fine("Git credential helper timed out");
                process.destroyForcibly();
                return null;
            }

            if (process.exitValue() != 0) {
                LOG.log(Level.FINE, "Git credential helper exited with {0}",
                        process.exitValue());
                return null;
            }

            String output = new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            return parse(output);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return null;
        } catch (IOException ex) {
            process.destroyForcibly();
            return null;
        }
    }

    static Credential parse(String output) {
        String username = null;
        String password = null;

        for (String line : output.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            if ("username".equals(key)) {
                username = value;
            } else if ("password".equals(key)) {
                password = value;
            } else if ("quit".equals(key) && "1".equals(value)) {
                return null;
            }
        }

        if (password == null || password.isBlank()) {
            return null;
        }
        return new Credential(username, password, Source.GIT_HELPER);
    }

    private static Credential readFromNetBeansKeyring(GitRemote remote) {
        GitURI base;
        try {
            base = new GitURI(remote.getRemoteUrl());
        } catch (URISyntaxException ex) {
            LOG.log(Level.FINE, "Cannot parse Git remote URI", ex);
            return null;
        }

        Set<String> users = new LinkedHashSet<>();
        if (base.getUser() != null && !base.getUser().isBlank()) {
            users.add(base.getUser());
        }

        Set<String> uriCandidates = new LinkedHashSet<>();
        for (StoredConnection stored : readStoredConnections()) {
            if (!stored.saveCredentials()) {
                continue;
            }
            if (!matchesRemote(stored.guriString(), remote)) {
                continue;
            }

            LOG.log(Level.FINE, "Matching NetBeans Git connection: {0}", stored);
            if (stored.user() != null && !stored.user().isBlank()) {
                users.add(stored.user());
            }
            try {
                GitURI storedUri = new GitURI(stored.guriString());
                if (stored.user() != null && !stored.user().isBlank()) {
                    uriCandidates.add(storedUri.setUser(stored.user())
                            .setPass(null).toString());
                }
                uriCandidates.add(storedUri.setUser(null).setPass(null).toString());
            } catch (URISyntaxException ex) {
                LOG.log(Level.FINE, "Cannot parse stored NetBeans Git URI", ex);
            }
        }

        String withoutCredentials = base.setUser(null).setPass(null).toString();
        uriCandidates.addAll(uriVariants(withoutCredentials));
        for (String user : users) {
            uriCandidates.addAll(uriVariants(
                    base.setUser(user).setPass(null).toString()));
        }

        LOG.log(Level.FINE, "NetBeans keyring candidates: {0}", uriCandidates);
        for (String uri : uriCandidates) {
            char[] value = readPassword(GIT_PASSWORD_KEY, uri);
            if (value != null && value.length > 0) {
                String token = new String(value);
                if (!token.isBlank()) {
                    LOG.log(Level.FINE,
                            "NetBeans keyring credential found for {0}", uri);
                    return new Credential(null, token, Source.NETBEANS_KEYRING);
                }
            }
        }
        return null;
    }

    /**
     * Reads the same key that {@code KeyringSupport} uses. Current NetBeans
     * versions store the plain concatenation; older versions used a SHA-1
     * suffix, so both are checked.
     */
    private static char[] readPassword(String key, String uri) {
        try {
            char[] value = Keyring.read(key + uri);
            if (value != null) {
                return value;
            }
            String hashed = sha1Hex(uri);
            return hashed == null ? null : Keyring.read(key + hashed);
        } catch (RuntimeException ex) {
            LOG.log(Level.FINE, "Cannot read NetBeans keyring entry", ex);
            return null;
        }
    }

    static String sha1Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes());
            StringBuilder result = new StringBuilder(hash.length * 3);
            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(hash[i] & 0xFF);
                if (hex.length() == 1) {
                    result.append('0');
                }
                result.append(hex);
                if (i < hash.length - 1) {
                    result.append(':');
                }
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            return null;
        }
    }

    private static List<StoredConnection> readStoredConnections() {
        List<StoredConnection> result = new ArrayList<>();
        try {
            Preferences preferences = NbPreferences.root()
                    .node(NETBEANS_GIT_PREFERENCES);
            for (String key : preferences.keys()) {
                if (!key.startsWith("recent_guri")) {
                    continue;
                }
                String value = preferences.get(key, "");
                String[] parts = value.split("<=~=>", -1);
                if (parts.length == 0) {
                    continue;
                }
                String guri = parts[0].trim();
                if (guri.isEmpty()) {
                    continue;
                }
                String user = parts.length > 1 && !parts[1].trim().isEmpty()
                        ? parts[1].trim() : null;
                boolean saveCredentials = parts.length <= 2
                        || "1".equals(parts[2].trim());
                result.add(new StoredConnection(guri, user, saveCredentials));
            }
        } catch (RuntimeException | BackingStoreException ex) {
            LOG.log(Level.FINE, "Cannot read NetBeans Git preferences", ex);
        }
        return result;
    }

    private static boolean matchesRemote(String storedUri, GitRemote remote) {
        String stored = normalizeForMatch(storedUri);
        String current = normalizeForMatch(remote.getRemoteUrl());
        return stored != null && stored.equals(current);
    }

    static String normalizeForMatch(String uri) {
        try {
            GitURI gitUri = new GitURI(uri);
            String scheme = gitUri.getScheme() == null
                    ? "" : gitUri.getScheme().toLowerCase(Locale.ROOT);
            String host = gitUri.getHost() == null
                    ? "" : gitUri.getHost().toLowerCase(Locale.ROOT);
            int port = gitUri.getPort();
            String path = gitUri.getPath();
            if (path == null) {
                path = "";
            }
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            while (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            if (path.endsWith(".git")) {
                path = path.substring(0, path.length() - 4);
            }

            StringBuilder result = new StringBuilder();
            result.append(scheme).append("://").append(host);
            if (port > 0) {
                result.append(':').append(port);
            }
            result.append('/').append(path);
            return result.toString();
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    static Set<String> uriVariants(String uri) {
        Set<String> result = new LinkedHashSet<>();
        if (uri == null || uri.isBlank()) {
            return result;
        }

        String trimmed = stripTrailingSlash(uri);
        result.add(trimmed);
        result.add(trimmed + "/");
        if (trimmed.endsWith(".git")) {
            result.add(trimmed.substring(0, trimmed.length() - 4));
        } else {
            result.add(trimmed + ".git");
        }
        return result;
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private static String webUrlWithGitSuffix(GitRemote remote) {
        String baseUrl = remote.getBaseUrl();
        String projectPath = remote.getProjectPath();
        if (baseUrl == null || baseUrl.isBlank()
                || projectPath == null || projectPath.isBlank()) {
            return null;
        }
        return baseUrl + "/" + projectPath + ".git";
    }
}
