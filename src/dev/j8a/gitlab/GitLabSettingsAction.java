package dev.j8a.gitlab;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import javax.swing.AbstractAction;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;

/**
 * Opens the GitLab settings for the repository selected in the IDE.
 */
public final class GitLabSettingsAction extends AbstractAction {

    public GitLabSettingsAction() {
        super(NbBundle.getMessage(GitLabSettingsAction.class,
                "GitLabSettingsAction.displayName"));
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        File projectDirectory = ProjectDirectoryFinder.find(
                Utilities.actionsGlobalContext());
        if (projectDirectory == null) {
            showError(NbBundle.getMessage(GitLabSettingsAction.class,
                    "GitLabSettingsAction.noProject"));
            return;
        }

        GitRepositoryInfo repository;
        try {
            repository = GitRepositoryInfo.read(projectDirectory);
        } catch (IOException ex) {
            showError(NbBundle.getMessage(GitLabSettingsAction.class,
                    "GitLabSettingsAction.noRepository"));
            return;
        }

        GitRepositoryInfo.Remote remote = repository.getPreferredRemote();
        if (remote == null) {
            showError(NbBundle.getMessage(GitLabSettingsAction.class,
                    "GitLabSettingsAction.noRemote"));
            return;
        }

        GitLabSettingsDialog.show(null, remote.getRemote());
    }

    private static void showError(String message) {
        DialogDisplayer.getDefault().notifyLater(new NotifyDescriptor(
                message,
                NbBundle.getMessage(GitLabSettingsAction.class,
                        "GitLabSettingsAction.errorTitle"),
                NotifyDescriptor.DEFAULT_OPTION,
                NotifyDescriptor.ERROR_MESSAGE,
                new Object[]{NotifyDescriptor.OK_OPTION},
                NotifyDescriptor.OK_OPTION));
    }
}
