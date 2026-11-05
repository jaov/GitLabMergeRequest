package dev.j8a.gitlab;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class GitRemoteTest {

    @Test
    public void parsesHttpsRemote() {
        GitRemote remote = GitRemote.parse("https://gitlab.com/group/project.git");

        assertEquals("group/project", remote.getProjectPath());
        assertEquals("https", remote.getScheme());
        assertEquals("gitlab.com", remote.getHost());
        assertEquals(-1, remote.getWebPort());
        assertEquals("https://gitlab.com", remote.getBaseUrl());
        assertEquals("https://gitlab.com/api/v4", remote.getApiUrl());
        assertEquals("https://gitlab.com/group/project", remote.getWebUrl());
        assertEquals("group%2Fproject", remote.getEncodedProjectPath());
    }

    @Test
    public void parsesHttpsRemoteWithPortAndSubgroup() {
        GitRemote remote = GitRemote.parse(
                "https://gitlab.example.com:8443/group/subgroup/project.git");

        assertEquals("group/subgroup/project", remote.getProjectPath());
        assertEquals(8443, remote.getWebPort());
        assertEquals("https://gitlab.example.com:8443", remote.getBaseUrl());
        assertEquals("https://gitlab.example.com:8443/group/subgroup/project",
                remote.getWebUrl());
    }

    @Test
    public void parsesScpLikeSshRemote() {
        GitRemote remote = GitRemote.parse("git@gitlab.com:group/project.git");

        assertEquals("ssh", remote.getScheme());
        assertEquals("group/project", remote.getProjectPath());
        assertEquals("https://gitlab.com", remote.getBaseUrl());
        assertEquals("https://gitlab.com/api/v4", remote.getApiUrl());
    }

    @Test
    public void ignoresSshPortForWebBaseUrl() {
        GitRemote remote = GitRemote.parse(
                "ssh://git@gitlab.example.com:2222/group/project.git");

        assertEquals("group/project", remote.getProjectPath());
        assertEquals(-1, remote.getWebPort());
        assertEquals("https://gitlab.example.com", remote.getBaseUrl());
    }

    @Test
    public void parsesHttpRemoteWithoutGitSuffix() {
        GitRemote remote = GitRemote.parse("http://gitlab.local/group/project");

        assertEquals("http://gitlab.local", remote.getBaseUrl());
        assertEquals("group/project", remote.getProjectPath());
        assertEquals("http://gitlab.local/group/project", remote.getWebUrl());
    }

    @Test
    public void stripsTrailingSlashes() {
        GitRemote remote = GitRemote.parse("https://gitlab.com/group/project.git/");

        assertEquals("group/project", remote.getProjectPath());
        assertEquals("https://gitlab.com/group/project", remote.getWebUrl());
    }

    @Test
    public void decodesAndReencodesProjectPath() {
        GitRemote remote = GitRemote.parse("https://gitlab.com/group/project%20name.git");

        assertEquals("group/project name", remote.getProjectPath());
        assertEquals("group%2Fproject%20name", remote.getEncodedProjectPath());
        assertEquals("https://gitlab.com/group/project%20name", remote.getWebUrl());
    }

    @Test
    public void rejectsMissingProjectPath() {
        assertThrows(IllegalArgumentException.class,
                () -> GitRemote.parse("https://gitlab.com/"));
    }

    @Test
    public void rejectsMissingHost() {
        assertThrows(IllegalArgumentException.class,
                () -> GitRemote.parse("file:///tmp/repo.git"));
    }

    @Test
    public void rejectsEmptyRemote() {
        assertThrows(IllegalArgumentException.class, () -> GitRemote.parse("   "));
    }
}
