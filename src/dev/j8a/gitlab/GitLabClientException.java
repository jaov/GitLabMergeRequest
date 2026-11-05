package dev.j8a.gitlab;

import java.io.IOException;

/**
 * A non-successful response from the GitLab API.
 */
public final class GitLabClientException extends IOException {

    private final int statusCode;
    private final String responseBody;

    public GitLabClientException(int statusCode, String message, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
