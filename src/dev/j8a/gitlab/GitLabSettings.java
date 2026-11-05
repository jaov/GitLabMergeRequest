package dev.j8a.gitlab;

import java.util.prefs.Preferences;
import org.netbeans.api.keyring.Keyring;
import org.openide.util.NbPreferences;

/**
 * Stores GitLab connection settings. Non-secret values live in NetBeans
 * preferences; personal access tokens are kept in the NetBeans keyring.
 */
public final class GitLabSettings {

    private static final String PREF_BASE_URL = "baseUrlOverride";
    private static final String PREF_DEFAULT_TARGET_BRANCH = "defaultTargetBranch";
    private static final String PREF_USE_GIT_CREDENTIALS = "useGitCredentials";
    private static final String TOKEN_KEY_PREFIX = "gitlab.token.";

    private GitLabSettings() {
    }

    private static Preferences preferences() {
        return NbPreferences.forModule(GitLabSettings.class);
    }

    public static String getBaseUrlOverride() {
        return preferences().get(PREF_BASE_URL, "");
    }

    public static void setBaseUrlOverride(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            preferences().remove(PREF_BASE_URL);
        } else {
            preferences().put(PREF_BASE_URL, stripTrailingSlash(baseUrl.trim()));
        }
    }

    public static String getDefaultTargetBranch() {
        return preferences().get(PREF_DEFAULT_TARGET_BRANCH, "");
    }

    public static void setDefaultTargetBranch(String branch) {
        if (branch == null || branch.isBlank()) {
            preferences().remove(PREF_DEFAULT_TARGET_BRANCH);
        } else {
            preferences().put(PREF_DEFAULT_TARGET_BRANCH, branch.trim());
        }
    }

    public static boolean getUseGitCredentials() {
        return preferences().getBoolean(PREF_USE_GIT_CREDENTIALS, true);
    }

    public static void setUseGitCredentials(boolean value) {
        preferences().putBoolean(PREF_USE_GIT_CREDENTIALS, value);
    }

    /**
     * Returns the configured base URL for the remote, or the remote's own base
     * URL when no override has been configured.
     */
    public static String resolveBaseUrl(GitRemote remote) {
        String override = getBaseUrlOverride();
        if (!override.isBlank()) {
            return stripTrailingSlash(override);
        }
        return remote.getBaseUrl();
    }

    public static String getToken(String host) {
        char[] value = Keyring.read(TOKEN_KEY_PREFIX + host);
        return value == null ? null : new String(value);
    }

    public static void setToken(String host, String token) {
        String key = TOKEN_KEY_PREFIX + host;
        if (token == null || token.isBlank()) {
            Keyring.delete(key);
        } else {
            Keyring.save(key, token.toCharArray(),
                    "GitLab personal access token for " + host);
        }
    }

    public static boolean hasToken(String host) {
        String token = getToken(host);
        return token != null && !token.isBlank();
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
