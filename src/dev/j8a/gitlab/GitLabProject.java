package dev.j8a.gitlab;

/**
 * The subset of a GitLab project returned by {@code GET /projects/:id}.
 */
public final class GitLabProject {

    private final String id;
    private final String pathWithNamespace;
    private final String defaultBranch;
    private final String webUrl;

    public GitLabProject(String id, String pathWithNamespace, String defaultBranch,
            String webUrl) {
        this.id = id;
        this.pathWithNamespace = pathWithNamespace;
        this.defaultBranch = defaultBranch;
        this.webUrl = webUrl;
    }

    public String getId() {
        return id;
    }

    public String getPathWithNamespace() {
        return pathWithNamespace;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public String getWebUrl() {
        return webUrl;
    }
}
