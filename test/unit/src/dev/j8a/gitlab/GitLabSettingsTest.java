package dev.j8a.gitlab;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GitLabSettingsTest {

    @Test
    public void resolvesBaseUrlOverride() {
        String original = GitLabSettings.getBaseUrlOverride();
        try {
            GitRemote remote = GitRemote.parse(
                    "https://gitlab.com/group/project.git");

            GitLabSettings.setBaseUrlOverride("");
            assertEquals("https://gitlab.com",
                    GitLabSettings.resolveBaseUrl(remote));

            GitLabSettings.setBaseUrlOverride(
                    "https://gitlab.example.com/gitlab/");
            assertEquals("https://gitlab.example.com/gitlab",
                    GitLabSettings.resolveBaseUrl(remote));
        } finally {
            GitLabSettings.setBaseUrlOverride(original);
        }
    }
}
