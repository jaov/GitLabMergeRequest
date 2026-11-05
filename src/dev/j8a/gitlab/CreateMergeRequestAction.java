package dev.j8a.gitlab;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;

/**
 * Initial scaffold action for creating a GitLab merge request.
 */
public final class CreateMergeRequestAction extends AbstractAction {

    public CreateMergeRequestAction() {
        super(NbBundle.getMessage(CreateMergeRequestAction.class,
                "CreateMergeRequestAction.displayName"));
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        DialogDisplayer.getDefault().notifyLater(new NotifyDescriptor.Message(
                NbBundle.getMessage(CreateMergeRequestAction.class,
                        "CreateMergeRequestAction.scaffold"),
                NotifyDescriptor.INFORMATION_MESSAGE));
    }
}
