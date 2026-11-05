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
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
            candidates.add(helperCredential);
        }
        Credential keyringCredential = readFromNetBeansKeyring(remote);
        if (keyringCredential != null
                && !containsToken(candidates, keyringCredential.token())) {
            candidates.add(keyringCredential);
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

        ProcessBuilder builder = new ProcessBuilder(gitCommand,
                "credential", "fill");
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("GCM_INTERACTIVE", "never");

        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            return null;
        }

        try {
            try (OutputStream input = process.getOutputStream()) {
                input.write(("url=" + url + "\n\n")
                        .getBytes(StandardCharsets.UTF_8));
            }

            if (!process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }

            if (process.exitValue() != 0) {
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
            return null;
        }

        Set<String> users = new LinkedHashSet<>();
        if (base.getUser() != null && !base.getUser().isBlank()) {
            users.add(base.getUser());
        }

        String withoutCredentials = base.setUser(null).setPass(null).toString();
        users.addAll(readRecentGuriUsers(withoutCredentials));

        Set<String> uriCandidates = new LinkedHashSet<>();
        uriCandidates.add(withoutCredentials);
        for (String user : users) {
            uriCandidates.add(base.setUser(user).setPass(null).toString());
        }

        for (String uri : uriCandidates) {
            char[] value = readPassword(GIT_PASSWORD_KEY, uri);
            if (value != null && value.length > 0) {
                String token = new String(value);
                if (!token.isBlank()) {
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

    /**
     * Reads the NetBeans Git module's recent connection entries to recover the
     * username that is part of the keyring key. This is intentionally
     * best-effort because the preference format is internal.
     */
    private static Set<String> readRecentGuriUsers(String uriWithoutCredentials) {
        Set<String> users = new LinkedHashSet<>();
        try {
            Preferences preferences = NbPreferences.root()
                    .node(NETBEANS_GIT_PREFERENCES);
            for (String key : preferences.keys()) {
                if (!key.startsWith("recent_guri")) {
                    continue;
                }
                String value = preferences.get(key, "");
                String[] parts = value.split("<=~=>", -1);
                if (parts.length < 2) {
                    continue;
                }
                String guri = parts[0].trim();
                if (!guri.equals(uriWithoutCredentials)) {
                    continue;
                }
                String user = parts[1].trim();
                if (!user.isEmpty()) {
                    users.add(user);
                }
            }
        } catch (RuntimeException | BackingStoreException ex) {
            // Preferences may be unavailable in tests or restricted environments.
        }
        return users;
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
