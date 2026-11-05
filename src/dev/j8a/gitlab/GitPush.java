package dev.j8a.gitlab;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Pushes a branch with the system Git executable so the user's existing
 * credential helper and SSH agent are used.
 */
final class GitPush {

    private GitPush() {
    }

    static void push(File repositoryRoot, String remoteName, String branch)
            throws IOException {
        Process process = new ProcessBuilder("git", "push", "-u", remoteName, branch)
                .directory(repositoryRoot)
                .redirectErrorStream(true)
                .start();

        String output = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("git push was interrupted", ex);
        }
        if (exitCode != 0) {
            throw new IOException("git push failed:\n" + output);
        }
    }
}
