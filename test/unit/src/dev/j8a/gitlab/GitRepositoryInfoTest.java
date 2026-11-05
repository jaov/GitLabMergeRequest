package dev.j8a.gitlab;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GitRepositoryInfoTest {

    @Test
    public void findsRepositoryRootFromNestedDirectory() throws IOException {
        Path root = Files.createTempDirectory("gitlab-find-root");
        Files.createDirectories(root.resolve(".git"));
        Path nested = root.resolve("src/main/java");
        Files.createDirectories(nested);

        assertEquals(root.toFile().getCanonicalFile(),
                GitRepositoryInfo.findRoot(nested.toFile()).getCanonicalFile());
    }

    @Test
    public void returnsNullWhenNoRepositoryExists() throws IOException {
        Path directory = Files.createTempDirectory("gitlab-no-repo");

        assertNull(GitRepositoryInfo.findRoot(directory.toFile()));
    }

    @Test
    public void readsRepositoryDetails() throws Exception {
        Assume.assumeTrue("git executable is required", gitAvailable());

        Path repository = Files.createTempDirectory("gitlab-repo");
        git(repository, "init", "-b", "main");
        git(repository, "config", "user.name", "Test");
        git(repository, "config", "user.email", "test@example.com");
        Files.writeString(repository.resolve("README.md"), "hello\n");
        git(repository, "add", ".");
        git(repository, "commit", "-m", "Initial commit");
        git(repository, "remote", "add", "origin",
                "https://gitlab.com/group/project.git");

        GitRepositoryInfo info = GitRepositoryInfo.read(repository.toFile());

        assertEquals("main", info.getActiveBranch());
        assertEquals("Initial commit", info.getLastCommitSubject());
        assertTrue(info.getLastCommitMessage().contains("Initial commit"));

        GitRepositoryInfo.Remote remote = info.getPreferredRemote();
        assertNotNull(remote);
        assertEquals("origin", remote.getName());
        assertEquals("group/project", remote.getRemote().getProjectPath());
        assertEquals("https://gitlab.com", remote.getRemote().getBaseUrl());
    }

    private static boolean gitAvailable() {
        try {
            Process process = new ProcessBuilder("git", "--version")
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException ex) {
            return false;
        }
    }

    private static void git(Path workDir, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));

        Process process = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        assertEquals("git " + String.join(" ", args) + " failed:\n" + output,
                0, exitCode);
    }
}
