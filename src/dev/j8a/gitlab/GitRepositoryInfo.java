package dev.j8a.gitlab;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.netbeans.libs.git.GitBranch;
import org.netbeans.libs.git.GitClient;
import org.netbeans.libs.git.GitException;
import org.netbeans.libs.git.GitRemoteConfig;
import org.netbeans.libs.git.GitRepository;
import org.netbeans.libs.git.GitRevisionInfo;
import org.netbeans.libs.git.progress.ProgressMonitor;

/**
 * Read-only view of the Git repository that contains a project directory.
 */
public final class GitRepositoryInfo {

    public static final class Remote {

        private final String name;
        private final GitRemote remote;

        private Remote(String name, GitRemote remote) {
            this.name = name;
            this.remote = remote;
        }

        public String getName() {
            return name;
        }

        public GitRemote getRemote() {
            return remote;
        }
    }

    private final File root;
    private final String activeBranch;
    private final List<Remote> remotes;
    private final String lastCommitSubject;
    private final String lastCommitMessage;

    private GitRepositoryInfo(File root, String activeBranch, List<Remote> remotes,
            String lastCommitSubject, String lastCommitMessage) {
        this.root = root;
        this.activeBranch = activeBranch;
        this.remotes = Collections.unmodifiableList(remotes);
        this.lastCommitSubject = lastCommitSubject;
        this.lastCommitMessage = lastCommitMessage;
    }

    /**
     * Walks up from {@code start} looking for a {@code .git} file or directory.
     */
    public static File findRoot(File start) {
        File current = start == null ? null : start.getAbsoluteFile();
        while (current != null) {
            if (new File(current, ".git").exists()) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }

    public static GitRepositoryInfo read(File projectDirectory) throws IOException {
        File root = findRoot(projectDirectory);
        if (root == null) {
            throw new IOException("Not a Git repository: " + projectDirectory);
        }

        ProgressMonitor monitor = new ProgressMonitor.DefaultProgressMonitor();
        try {
            GitRepository repository = GitRepository.getInstance(root);
            try (GitClient client = repository.createClient()) {
                String active = readActiveBranch(client, monitor);
                List<Remote> remotes = readRemotes(client, monitor);
                GitRevisionInfo head = readHead(client, monitor);

                String subject = head == null ? "" : head.getShortMessage();
                String message = head == null ? "" : head.getFullMessage();
                return new GitRepositoryInfo(root, active, remotes, subject, message);
            }
        } catch (GitException ex) {
            throw new IOException("Could not read Git repository: " + root, ex);
        }
    }

    private static String readActiveBranch(GitClient client, ProgressMonitor monitor)
            throws GitException {
        Map<String, GitBranch> branches = client.getBranches(false, monitor);
        for (GitBranch branch : branches.values()) {
            if (branch.isActive()) {
                return branch.getName();
            }
        }
        return null;
    }

    private static List<Remote> readRemotes(GitClient client, ProgressMonitor monitor)
            throws GitException {
        Map<String, GitRemoteConfig> configs = client.getRemotes(monitor);
        List<Remote> result = new ArrayList<>();
        for (Map.Entry<String, GitRemoteConfig> entry : configs.entrySet()) {
            for (String uri : entry.getValue().getUris()) {
                try {
                    result.add(new Remote(entry.getKey(), GitRemote.parse(uri)));
                } catch (IllegalArgumentException ex) {
                    // Ignore remotes that are not usable URLs (local paths, malformed entries).
                }
            }
        }
        return result;
    }

    private static GitRevisionInfo readHead(GitClient client, ProgressMonitor monitor)
            throws GitException {
        try {
            return client.log("HEAD", monitor);
        } catch (GitException ex) {
            // An empty repository has no HEAD commit yet.
            return null;
        }
    }

    public File getRoot() {
        return root;
    }

    public String getActiveBranch() {
        return activeBranch;
    }

    public List<Remote> getRemotes() {
        return remotes;
    }

    /**
     * Returns the {@code origin} remote when present, otherwise the first remote.
     */
    public Remote getPreferredRemote() {
        for (Remote remote : remotes) {
            if ("origin".equals(remote.getName())) {
                return remote;
            }
        }
        return remotes.isEmpty() ? null : remotes.get(0);
    }

    public String getLastCommitSubject() {
        return lastCommitSubject;
    }

    public String getLastCommitMessage() {
        return lastCommitMessage;
    }
}
