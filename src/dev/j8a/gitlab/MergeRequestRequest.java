package dev.j8a.gitlab;

import org.json.simple.JSONObject;

/**
 * Values sent to {@code POST /projects/:id/merge_requests}.
 */
public final class MergeRequestRequest {

    private final String sourceBranch;
    private final String targetBranch;
    private final String title;
    private final String description;
    private final boolean removeSourceBranch;
    private final boolean squash;
    private final boolean draft;

    public MergeRequestRequest(String sourceBranch, String targetBranch, String title,
            String description, boolean removeSourceBranch, boolean squash, boolean draft) {
        this.sourceBranch = sourceBranch;
        this.targetBranch = targetBranch;
        this.title = title;
        this.description = description;
        this.removeSourceBranch = removeSourceBranch;
        this.squash = squash;
        this.draft = draft;
    }

    public String getSourceBranch() {
        return sourceBranch;
    }

    public String getTargetBranch() {
        return targetBranch;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRemoveSourceBranch() {
        return removeSourceBranch;
    }

    public boolean isSquash() {
        return squash;
    }

    public boolean isDraft() {
        return draft;
    }

    /**
     * Builds the GitLab API payload. Draft status is expressed with the
     * {@code Draft: } title prefix so it also works with older GitLab versions.
     */
    @SuppressWarnings("unchecked")
    JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("source_branch", sourceBranch);
        json.put("target_branch", targetBranch);
        json.put("title", draft ? draftTitle(title) : title);
        if (description != null && !description.isBlank()) {
            json.put("description", description);
        }
        json.put("remove_source_branch", removeSourceBranch);
        json.put("squash", squash);
        return json;
    }

    private static String draftTitle(String value) {
        if (value == null) {
            return "Draft: ";
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("draft:") || lower.startsWith("draft ")) {
            return value;
        }
        return "Draft: " + value;
    }
}
