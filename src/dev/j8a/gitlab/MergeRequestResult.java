package dev.j8a.gitlab;

/**
 * The subset of a GitLab merge request returned after creation.
 */
public final class MergeRequestResult {

    private final long iid;
    private final String webUrl;
    private final String title;
    private final String state;

    public MergeRequestResult(long iid, String webUrl, String title, String state) {
        this.iid = iid;
        this.webUrl = webUrl;
        this.title = title;
        this.state = state;
    }

    public long getIid() {
        return iid;
    }

    public String getWebUrl() {
        return webUrl;
    }

    public String getTitle() {
        return title;
    }

    public String getState() {
        return state;
    }
}
