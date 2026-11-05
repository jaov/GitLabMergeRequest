# GitLab Merge Request

A small Apache NetBeans module for creating GitLab merge requests from the IDE.

## Current features

- **Create Merge Request...** action under **Team → Git** and in the project
  context menu.
- Detects the Git repository, active branch, remote, and last commit.
- Supports GitLab.com and self-hosted GitLab instances.
- Reads the project path from HTTPS, HTTP, SSH, `git://`, and scp-like remotes
  such as `git@gitlab.com:group/project.git`.
- Loads the project's default branch and branches from the GitLab API.
- Prefills the merge request title and description from the last commit.
- Options for draft, squash, source branch deletion, and pushing the current
  branch before creating the merge request.
- Reuses an existing Git credential (including tokens managed by `glab`,
  Git Credential Manager, libsecret, or `git credential-store`) before asking
  for a token.
- Falls back to the NetBeans Git keyring when the Git credential helper has
  nothing.
- Stores the personal access token in the NetBeans keyring.
- Optional base URL override for GitLab installations served under a path.

## Requirements

- Apache NetBeans 30 (built against it)
- JDK 17 or newer (the module targets Java 17; NetBeans 30 itself requires
  Java 21+)
- A GitLab personal access token with the `api` scope
- Git installed on `PATH` if you use **Push current branch first**

## Build

```bash
ant -f build.xml clean nbm
```

The installable module is written to:

```text
build/dev-j8a-gitlab.nbm
```

Run the tests with:

```bash
ant -f build.xml test
```

## Install

1. Open **Tools → Plugins → Downloaded**.
2. Click **Add Plugins...**.
3. Select `build/dev-j8a-gitlab.nbm`.
4. Install the plugin and restart NetBeans if prompted.

Alternatively, open the project in NetBeans and choose **Run → Run Project**
to launch a development IDE with the module installed.

## Usage

1. Open a project whose Git remote points at GitLab.
2. Right-click the project and choose **Create Merge Request...**, or use
   **Team → Git → Create Merge Request...**.
3. On first use, the plugin tries to reuse an existing Git credential. If none
   is available, enter a GitLab personal access token. The token is saved in
   the NetBeans keyring.
4. Review or change the source branch, target branch, title, and description.
5. Choose the merge request options and click **OK**.
6. When the merge request is created, use **Open in Browser** to view it.

Use **Team → Git → GitLab Settings...** to change the token, clear it, or set a
base URL override.

## Credential reuse

Before showing the token field, the plugin tries, in order:

1. `git credential fill` for the remote's HTTPS URL. This covers credential
   helpers such as `glab auth git-credential`, Git Credential Manager,
   libsecret, and `git credential-store`.
2. The password saved by NetBeans' own Git module in the NetBeans keyring.

Reused credentials are validated against the GitLab API. If the credential is
missing, rejected, or cannot create merge requests, the plugin falls back to
GitLab Settings. Disable reuse with **Use existing Git credentials when
available** in the settings dialog.

The reused token must have the `api` scope to create merge requests. A token
with only `read_repository`/`write_repository` can be used for Git operations
but will be rejected when creating the merge request.

## Settings

- **Use existing Git credentials when available** — try the Git credential
  helper and the NetBeans Git keyring first.
- **Base URL override** — leave empty to use the URL derived from the Git
  remote. Set it when the GitLab web UI is served from a different URL or path,
  for example `https://gitlab.example.com/gitlab`.
- **Personal access token** — stored in the NetBeans keyring, not in plain
  preferences.

## Notes and limitations

- The Git remote project is assumed to be the merge request target project.
  Fork-based workflows need a later "target project" option.
- Self-hosted GitLab installations with a URL context path may need the base
  URL override.
- The branch list uses the first page of GitLab results (up to 100 branches).
- Self-signed TLS certificates are not supported yet unless they are trusted by
  the JVM.
- The push option runs the system `git` executable so the user's credential
  helper and SSH agent are used.
