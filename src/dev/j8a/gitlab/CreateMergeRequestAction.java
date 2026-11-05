package dev.j8a.gitlab;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.project.Project;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.HtmlBrowser.URLDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;

/**
 * Entry point for creating a GitLab merge request from the current project.
 */
public final class CreateMergeRequestAction extends AbstractAction {

    private static final RequestProcessor RP = new RequestProcessor(
            CreateMergeRequestAction.class);

    public CreateMergeRequestAction() {
        super(NbBundle.getMessage(CreateMergeRequestAction.class,
                "CreateMergeRequestAction.displayName"));
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        Lookup context = Utilities.actionsGlobalContext();
        File projectDirectory = findProjectDirectory(context);
        if (projectDirectory == null) {
            showError(NbBundle.getMessage(CreateMergeRequestAction.class,
                    "CreateMergeRequestAction.noProject"));
            return;
        }

        GitRepositoryInfo repository;
        try {
            repository = GitRepositoryInfo.read(projectDirectory);
        } catch (IOException ex) {
            showError(messageOf(ex, NbBundle.getMessage(CreateMergeRequestAction.class,
                    "CreateMergeRequestAction.noRepository")));
            return;
        }

        GitRepositoryInfo.Remote preferredRemote = repository.getPreferredRemote();
        if (preferredRemote == null) {
            showError(NbBundle.getMessage(CreateMergeRequestAction.class,
                    "CreateMergeRequestAction.noRemote"));
            return;
        }

        GitRemote remote = preferredRemote.getRemote();
        String token = ensureToken(remote);
        if (token == null) {
            return;
        }

        String baseUrl = GitLabSettings.resolveBaseUrl(remote);
        String encodedProjectPath = remote.getEncodedProjectPath();
        loadProject(repository, preferredRemote, baseUrl, token, encodedProjectPath);
    }

    private String ensureToken(GitRemote remote) {
        String token = GitLabSettings.getToken(remote.getHost());
        if (token != null && !token.isBlank()) {
            return token;
        }

        if (!GitLabSettingsDialog.show(null, remote)) {
            return null;
        }

        token = GitLabSettings.getToken(remote.getHost());
        if (token == null || token.isBlank()) {
            showError(NbBundle.getMessage(CreateMergeRequestAction.class,
                    "CreateMergeRequestAction.noToken"));
            return null;
        }
        return token;
    }

    private void loadProject(GitRepositoryInfo repository,
            GitRepositoryInfo.Remote preferredRemote, String baseUrl, String token,
            String encodedProjectPath) {
        RP.post(() -> {
            ProgressHandle handle = ProgressHandle.createHandle(
                    NbBundle.getMessage(CreateMergeRequestAction.class,
                            "CreateMergeRequestAction.loading"));
            handle.start();
            try {
                GitLabClient client = new GitLabClient(baseUrl, token);
                GitLabProject project = client.getProject(encodedProjectPath);
                List<String> branches = client.getBranches(encodedProjectPath);

                SwingUtilities.invokeLater(() -> showDialog(
                        repository, preferredRemote, client, project, branches,
                        encodedProjectPath));
            } catch (IOException ex) {
                showErrorLater(messageOf(ex, NbBundle.getMessage(
                        CreateMergeRequestAction.class,
                        "CreateMergeRequestAction.loadFailed")));
            } finally {
                handle.finish();
            }
        });
    }

    private void showDialog(GitRepositoryInfo repository,
            GitRepositoryInfo.Remote preferredRemote, GitLabClient client,
            GitLabProject project, List<String> branches,
            String encodedProjectPath) {
        String activeBranch = repository.getActiveBranch();
        List<String> sourceBranches = new ArrayList<>(branches);
        if (activeBranch != null && !activeBranch.isBlank()
                && !sourceBranches.contains(activeBranch)) {
            sourceBranches.add(0, activeBranch);
        }

        String targetBranch = chooseTargetBranch(project, branches);
        MergeRequestPanel panel = MergeRequestDialog.show(null,
                project.getPathWithNamespace(),
                activeBranch == null ? "" : activeBranch,
                targetBranch,
                sourceBranches,
                branches,
                repository.getLastCommitSubject(),
                repository.getLastCommitMessage());
        if (panel == null) {
            return;
        }

        MergeRequestRequest request = panel.toRequest();
        boolean pushFirst = panel.isPushFirst();
        createMergeRequest(client, repository, preferredRemote,
                encodedProjectPath, request, pushFirst);
    }

    private void createMergeRequest(GitLabClient client,
            GitRepositoryInfo repository, GitRepositoryInfo.Remote preferredRemote,
            String encodedProjectPath, MergeRequestRequest request,
            boolean pushFirst) {
        RP.post(() -> {
            ProgressHandle handle = ProgressHandle.createHandle(
                    NbBundle.getMessage(CreateMergeRequestAction.class,
                            "CreateMergeRequestAction.creating"));
            handle.start();
            try {
                if (pushFirst) {
                    GitPush.push(repository.getRoot(), preferredRemote.getName(),
                            request.getSourceBranch());
                }
                MergeRequestResult result = client.createMergeRequest(
                        encodedProjectPath, request);
                SwingUtilities.invokeLater(() -> showSuccess(result));
            } catch (IOException ex) {
                showErrorLater(messageOf(ex, NbBundle.getMessage(
                        CreateMergeRequestAction.class,
                        "CreateMergeRequestAction.createFailed")));
            } finally {
                handle.finish();
            }
        });
    }

    private static String chooseTargetBranch(GitLabProject project,
            List<String> branches) {
        String configured = GitLabSettings.getDefaultTargetBranch();
        if (!configured.isBlank() && branches.contains(configured)) {
            return configured;
        }
        String projectDefault = project.getDefaultBranch();
        if (projectDefault != null && !projectDefault.isBlank()) {
            return projectDefault;
        }
        if (branches.contains("main")) {
            return "main";
        }
        if (branches.contains("master")) {
            return "master";
        }
        return branches.isEmpty() ? "" : branches.get(0);
    }

    private static File findProjectDirectory(Lookup context) {
        Project project = context.lookup(Project.class);
        if (project != null) {
            File directory = FileUtil.toFile(project.getProjectDirectory());
            if (directory != null) {
                return directory;
            }
        }

        FileObject file = context.lookup(FileObject.class);
        if (file != null) {
            File directory = FileUtil.toFile(file);
            if (directory != null) {
                return directory;
            }
        }

        DataObject dataObject = context.lookup(DataObject.class);
        if (dataObject != null) {
            File directory = FileUtil.toFile(dataObject.getPrimaryFile());
            if (directory != null) {
                return directory;
            }
        }
        return null;
    }

    private static void showSuccess(MergeRequestResult result) {
        String openLabel = NbBundle.getMessage(CreateMergeRequestAction.class,
                "CreateMergeRequestAction.openInBrowser");
        NotifyDescriptor descriptor = new NotifyDescriptor(
                NbBundle.getMessage(CreateMergeRequestAction.class,
                        "CreateMergeRequestAction.success",
                        result.getIid(), result.getWebUrl()),
                NbBundle.getMessage(CreateMergeRequestAction.class,
                        "CreateMergeRequestAction.successTitle"),
                NotifyDescriptor.DEFAULT_OPTION,
                NotifyDescriptor.INFORMATION_MESSAGE,
                new Object[]{openLabel, NotifyDescriptor.CANCEL_OPTION},
                openLabel);

        Object selected = DialogDisplayer.getDefault().notify(descriptor);
        if (selected == openLabel) {
            try {
                URLDisplayer.getDefault().showURLExternal(
                        URI.create(result.getWebUrl()).toURL());
            } catch (Exception ex) {
                showError(messageOf(ex, result.getWebUrl()));
            }
        }
    }

    private static void showErrorLater(String message) {
        SwingUtilities.invokeLater(() -> showError(message));
    }

    private static void showError(String message) {
        DialogDisplayer.getDefault().notifyLater(new NotifyDescriptor(
                message,
                NbBundle.getMessage(CreateMergeRequestAction.class,
                        "CreateMergeRequestAction.errorTitle"),
                NotifyDescriptor.DEFAULT_OPTION,
                NotifyDescriptor.ERROR_MESSAGE,
                new Object[]{NotifyDescriptor.OK_OPTION},
                NotifyDescriptor.OK_OPTION));
    }

    private static String messageOf(Throwable throwable, String fallback) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }
}
