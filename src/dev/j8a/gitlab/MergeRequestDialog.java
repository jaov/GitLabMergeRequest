package dev.j8a.gitlab;

import java.awt.Component;
import java.awt.Dialog;
import java.util.List;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;

/**
 * Shows the merge request form and returns it when the user confirms.
 */
final class MergeRequestDialog {

    private MergeRequestDialog() {
    }

    static MergeRequestPanel show(Component parent, String projectPath,
            String sourceBranch, String targetBranch,
            List<String> sourceBranches, List<String> targetBranches,
            String title, String description) {
        MergeRequestPanel panel = new MergeRequestPanel(projectPath, sourceBranch,
                targetBranch, sourceBranches, targetBranches, title, description);

        DialogDescriptor descriptor = new DialogDescriptor(panel,
                NbBundle.getMessage(MergeRequestDialog.class,
                        "MergeRequestDialog.title"));
        descriptor.setOptionType(NotifyDescriptor.OK_CANCEL_OPTION);
        descriptor.setMessageType(NotifyDescriptor.PLAIN_MESSAGE);
        descriptor.setValid(panel.isValidForm());
        panel.addChangeListener(event -> descriptor.setValid(panel.isValidForm()));

        Dialog dialog = DialogDisplayer.getDefault().createDialog(descriptor);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        dialog.dispose();

        return descriptor.getValue() == NotifyDescriptor.OK_OPTION ? panel : null;
    }
}
