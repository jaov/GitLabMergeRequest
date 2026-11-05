package dev.j8a.gitlab;

import java.awt.Component;
import java.awt.Dialog;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;

/**
 * Shows the GitLab connection settings for a detected remote.
 */
final class GitLabSettingsDialog {

    private GitLabSettingsDialog() {
    }

    static boolean show(Component parent, GitRemote remote) {
        GitLabSettingsPanel panel = new GitLabSettingsPanel(remote);

        DialogDescriptor descriptor = new DialogDescriptor(panel,
                NbBundle.getMessage(GitLabSettingsDialog.class,
                        "GitLabSettingsDialog.title"));
        descriptor.setOptionType(NotifyDescriptor.OK_CANCEL_OPTION);
        descriptor.setMessageType(NotifyDescriptor.PLAIN_MESSAGE);
        descriptor.setValid(panel.isValidForm());
        panel.addChangeListener(event -> descriptor.setValid(panel.isValidForm()));

        Dialog dialog = DialogDisplayer.getDefault().createDialog(descriptor);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        dialog.dispose();

        if (descriptor.getValue() == NotifyDescriptor.OK_OPTION) {
            panel.save();
            return true;
        }
        return false;
    }
}
