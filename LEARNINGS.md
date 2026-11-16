# Learnings

Notes from building the **GitLab Merge Request** NetBeans module.

## NetBeans module basics

- A standalone NetBeans module is mostly:
  - `manifest.mf` — module identity and layer/bundle registration.
  - `nbproject/project.xml` — module dependencies and standalone flag.
  - `nbproject/project.properties` — `javac.source` / `javac.target`.
  - `src/.../layer.xml` — registration of actions, menus, and services.
  - `src/.../Bundle.properties` — localized names and messages.
  - `build.xml` — imports the NetBeans harness build.
- The Value Class Wizard project was a good reference for the skeleton:
  - `nbproject/build-impl.xml` is a thin wrapper around the harness.
  - `nbproject/platform.properties` defines the cluster path.
  - `nbproject/private/platform-private.properties` points at the local NetBeans
    installation and must not be committed.
- `ant -f build.xml clean nbm` produces the installable module in `build/`.
- Java 17 source/target works even though NetBeans 30 runs on Java 21+.

## Menus and layer registration

- `Actions/Git` is an **action pool**, not the Team menu.
  - Registering an action there makes it available for shortcuts and toolbars,
    but it does not automatically add it to the Team menu.
- The Team menu is built dynamically by the versioning core
  (`VersioningMainMenu`).
- The correct extension point for extra Team menu items is:

  ```text
  Actions/Versioning/Additional
  ```

  Actions looked up from that folder are appended to the Team menu for the
  current context.
- Project context menu actions belong under:

  ```text
  Projects/Actions
  ```

- Static menu items can be disabled conditionally with `Action.isEnabled()`.
  The Team menu is rebuilt when opened, so the enabled state is re-evaluated.
- `Actions/Versioning/Additional` items are shown for every versioning system,
  so the action must check for a Git repository itself.

## Git APIs in NetBeans

- `org.netbeans.libs.git` is a public and useful API:
  - `GitRepository.getInstance(root)` opens a working tree.
  - `GitClient` exposes branches, remotes, status, log, push, etc.
  - `GitBranch.isActive()` identifies the current branch.
  - `GitRemoteConfig.getUris()` gives the remote URLs.
  - `GitRevisionInfo` gives the last commit subject and body.
- An empty repository has no `HEAD` commit; `log("HEAD")` can throw.
  Catch that case and treat the message as empty.
- `GitClient` implements `AutoCloseable`, so use try-with-resources.
- The library module needs `slf4j` on the test classpath. Adding
  `slf4j.api` and `slf4j.jdk14` as test dependencies fixed
  `NoClassDefFoundError: org/slf4j/LoggerFactory`.

## Remote URL parsing

- Git remotes come in several shapes:
  - `https://gitlab.com/group/project.git`
  - `https://gitlab.example.com:8443/group/sub/project.git`
  - `git@gitlab.com:group/project.git`
  - `ssh://git@gitlab.example.com:2222/group/project.git`
  - `git://host/group/project.git`
- For scp-like URLs, convert to a URI by inserting a slash after the host:
  `git@host:group/project.git` → `ssh://git@host/group/project.git`.
- The SSH port is not the web port. For SSH and `git://` remotes, default the
  web base URL to HTTPS and drop the port. The user can override it.
- GitLab API project IDs need the **whole path** URL-encoded:
  `group/project` → `group%2Fproject`.
- Web URLs keep the slashes:
  `https://gitlab.com/group/project`.
- Use `URI.getPath()` when parsing because it decodes percent-encoded paths.

## GitLab API

- The useful endpoints for this module were:
  - `GET /api/v4/projects/:id`
  - `GET /api/v4/projects/:id/repository/branches`
  - `POST /api/v4/projects/:id/merge_requests`
  - `GET /api/v4/user`
- `Authorization: Bearer <token>` works for both personal access tokens and
  OAuth2 access tokens. `PRIVATE-TOKEN` is aimed at personal access tokens.
  Using Bearer simplified credential reuse.
- OAuth2 access tokens can be short-lived. Re-reading the credential helper is
  safer than caching the token for the whole IDE session.
- Creating a merge request needs the `api` scope. A token with only
  `read_repository`/`write_repository` can push but will fail when creating
  the MR.
- Error responses can contain:
  - `{"message": "401 Unauthorized"}`
  - `{"message": {"source_branch": ["can't be blank"]}}`
  - The client should flatten both forms into a readable message.
- Draft merge requests can be created by prefixing the title with `Draft: `.
  This is more portable than relying on a newer API parameter.
- Branch listing is paginated; the first page is enough for an MVP but a
  production version should follow pagination or use server-side search.

## Credential reuse

- `git credential fill` is the cleanest way to reuse what Git already uses:
  - glab
  - Git Credential Manager
  - libsecret
  - `git credential-store`
  - SSH agent is not involved for API tokens
- Use `url=<https-url>` as the input and parse `username`/`password` lines.
- Disable prompts and add a timeout:

  ```text
  GIT_TERMINAL_PROMPT=0
  GCM_INTERACTIVE=never
  ```

- A helper can return an expired or invalid token. Always validate the token
  against the API and try the next source on `401`/`403`.
- NetBeans' own Git module stores HTTPS passwords in the NetBeans keyring:
  - Key prefix: `guri_password`
  - Key: prefix + URI string (plain concatenation)
  - Older NetBeans versions used a SHA-1 suffix in colon-separated hex form.
- `org.netbeans.modules.versioning.util` is a friends-only module. Depending on
  `KeyringSupport` directly fails the harness with “not a friend”. Reading the
  keyring with the public `org.netbeans.api.keyring.Keyring` API and the same
  key format avoids that dependency.
- The NetBeans Git module keeps the username separately in `recent_guri.*`
  preferences under `org/netbeans/modules/git`. Reading those is best-effort
  because the format is internal.
- Reconstructing the URI from the remote is not always enough. NetBeans may
  store it with or without `.git`, with a trailing slash, or with a different
  username. Read the exact `recent_guri` entry and use its `guriString` and
  username for the keyring key; also try URI variants.
- The `recent_guri` entry layout is:
  `guri <=~=> user <=~=> saveCredentials <=~=> privateKeyAuth <=~=> identityFile`.
  If `saveCredentials` is `0`, there is no keyring entry to read.
- Credentials are sensitive:
  - Never log the token.
  - Do not store a reused token unless the user explicitly saved one.
  - Report only whether a credential was found and which source produced it.

## Threading and UI

- Keep repository and HTTP work off the EDT.
  - Use `RequestProcessor` for Git/API work.
  - Use `SwingUtilities.invokeLater` to show dialogs and update the UI.
- `DialogDisplayer.notifyLater` is useful for showing errors from background
  threads.
- `ProgressHandle` provides feedback for API calls.
- `StatusDisplayer` is enough for a short “looking for credentials” message.
- The main menu is dynamic; it is rebuilt when opened, so actions can read the
  current selection and enable/disable themselves accordingly.

## Testing

- The NetBeans harness runs JUnit 4 tests with:

  ```bash
  ant -f build.xml test
  ```

- Useful test patterns:
  - `com.sun.net.httpserver.HttpServer` for a fake GitLab API.
  - Temporary Git repositories created with the `git` CLI for repository
    reading tests.
  - Temporary executable shell scripts as fake credential helpers.
  - `Assume` to skip tests when `git` or the keyring is unavailable.
- Test dependencies are declared separately from module dependencies.
- The test classpath needs `slf4j` for `org.netbeans.libs.git`.
- Add a registration test for layer entries; it catches broken paths and
  invalid `instanceCreate` attributes early.

## Versioning and releases

- `OpenIDE-Module-Specification-Version` is what drives upgrade detection.
  Bumping `0.1` to `0.2` made NetBeans offer the new NBM as an update.
- NetBeans 23 ships `org.netbeans.libs.json_simple/1` at spec `0.37`, while
  newer platforms ship `0.44`. Declaring a dependency on `0.44` prevents the
  module from loading on NB23. Declare a lower spec version (`0.1`) when the
  API used is stable across both.
- The harness writes `OpenIDE-Module-Java-Dependencies` from `javac.release`
  (or `javac.target`). A module built with `javac.target=17` says `Java > 17`,
  which means Java 18+ and excludes NB23 running on JDK 17. Building with
  `javac.release=16` makes it `Java > 16` (Java 17+) while still allowing
  records and `instanceof` patterns.
- `OpenIDE-Module-Implementation-Version` is generated by the harness from the
  build date, so the specification version is the reliable release marker.
- The NBM metadata (`Info/info.xml`) and the module jar manifest both need to
  show the new version.
- Publishing with GitHub CLI:

  ```bash
  gh repo create GitLabMergeRequest --public --source=. --remote=origin --push
  git tag -a v0.2.0 -m "GitLab Merge Request 0.2.0"
  git push origin v0.2.0
  gh release create v0.2.0 build/dev-j8a-gitlab.nbm \
      --title "GitLab Merge Request 0.2.0" \
      --notes-file release-notes.md
  ```

- Attaching the NBM to the release makes installation a one-file download.

## Things I would do differently next time

- Research the Team menu extension point before wiring any actions; the
  difference between `Actions/Git` and `Actions/Versioning/Additional` is not
  obvious.
- Prototype `git credential fill` early; it determines whether the plugin can
  avoid asking for a token at all.
- Add an optional integration test gated by a system property for real GitLab
  credentials, so the normal test suite stays deterministic.
- Consider OAuth2 token refresh handling explicitly instead of relying on the
  external credential helper.
- Add branch pagination and merge-request listing in a later iteration; the
  current code is intentionally an MVP.
