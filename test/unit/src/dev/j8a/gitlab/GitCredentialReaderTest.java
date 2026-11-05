package dev.j8a.gitlab;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

public class GitCredentialReaderTest {

    @Test
    public void parsesCredentialOutput() {
        GitCredentialReader.Credential credential = GitCredentialReader.parse(
                "protocol=https\nhost=gitlab.com\nusername=oauth2\npassword=secret\n");

        assertNotNull(credential);
        assertEquals("oauth2", credential.username());
        assertEquals("secret", credential.token());
        assertEquals(GitCredentialReader.Source.GIT_HELPER, credential.source());
    }

    @Test
    public void ignoresEmptyPassword() {
        assertNull(GitCredentialReader.parse(
                "username=oauth2\npassword=\n"));
    }

    @Test
    public void ignoresQuitResponse() {
        assertNull(GitCredentialReader.parse(
                "username=oauth2\npassword=secret\nquit=1\n"));
    }

    @Test
    public void hashesLegacyKeyringSuffixLikeKeyringSupport() {
        assertEquals(
                "14:c2:e4:53:87:e3:df:c2:94:10:d8:c9:f3:6c:ea:39:f0:84:48:79",
                GitCredentialReader.sha1Hex(
                        "https://gitlab.com/group/project.git"));
    }

    @Test
    public void readsCredentialFromFakeHelper() throws Exception {
        Assume.assumeFalse(isWindows());
        Path helper = createHelper("""
                #!/bin/sh
                cat >/dev/null
                echo username=oauth2
                echo password=test-token
                """);

        GitRemote remote = GitRemote.parse(
                "https://gitlab.invalid/group/project.git");
        GitCredentialReader.Credential credential = GitCredentialReader.read(
                remote, helper.toString());

        assertNotNull(credential);
        assertEquals("oauth2", credential.username());
        assertEquals("test-token", credential.token());
        assertEquals(GitCredentialReader.Source.GIT_HELPER, credential.source());
    }

    @Test
    public void returnsNullWhenHelperHasNoPassword() throws Exception {
        Assume.assumeFalse(isWindows());
        Path helper = createHelper("""
                #!/bin/sh
                cat >/dev/null
                echo username=oauth2
                """);

        GitRemote remote = GitRemote.parse(
                "https://gitlab.invalid/group/project.git");

        assertNull(GitCredentialReader.read(remote, helper.toString()));
    }

    @Test
    public void returnsNullWhenHelperFails() throws Exception {
        Assume.assumeFalse(isWindows());
        Path helper = createHelper("""
                #!/bin/sh
                cat >/dev/null
                exit 1
                """);

        GitRemote remote = GitRemote.parse(
                "https://gitlab.invalid/group/project.git");

        assertNull(GitCredentialReader.read(remote, helper.toString()));
    }

    @Test
    public void returnsNullWhenGitCommandIsMissing() {
        GitRemote remote = GitRemote.parse(
                "https://gitlab.invalid/group/project.git");

        assertNull(GitCredentialReader.read(remote,
                "/definitely/not/a/git/executable"));
    }

    private static Path createHelper(String content) throws IOException {
        Path helper = Files.createTempFile("gitlab-credential-helper", ".sh");
        Files.writeString(helper, content, StandardCharsets.UTF_8);
        helper.toFile().setExecutable(true);
        return helper;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("win");
    }
}
