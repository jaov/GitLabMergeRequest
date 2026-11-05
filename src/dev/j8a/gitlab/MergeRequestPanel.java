package dev.j8a.gitlab;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.util.ChangeSupport;
import org.openide.util.NbBundle;

/**
 * Form used to collect merge request details.
 */
final class MergeRequestPanel extends JPanel {

    private final ChangeSupport changeSupport = new ChangeSupport(this);
    private final JComboBox<String> sourceBranchCombo;
    private final JComboBox<String> targetBranchCombo;
    private final JTextField titleField = new JTextField();
    private final JTextArea descriptionArea = new JTextArea(6, 40);
    private final JCheckBox draftCheckBox;
    private final JCheckBox squashCheckBox;
    private final JCheckBox removeSourceBranchCheckBox;
    private final JCheckBox pushFirstCheckBox;

    MergeRequestPanel(String projectPath, String sourceBranch, String targetBranch,
            List<String> sourceBranches, List<String> targetBranches,
            String title, String description) {
        super(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        sourceBranchCombo = new JComboBox<>(sourceBranches.toArray(new String[0]));
        sourceBranchCombo.setEditable(true);
        sourceBranchCombo.setSelectedItem(sourceBranch);

        targetBranchCombo = new JComboBox<>(targetBranches.toArray(new String[0]));
        targetBranchCombo.setEditable(true);
        targetBranchCombo.setSelectedItem(targetBranch);

        titleField.setText(title);
        descriptionArea.setText(description);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);

        draftCheckBox = new JCheckBox(NbBundle.getMessage(
                MergeRequestPanel.class, "MergeRequestPanel.draft"));
        squashCheckBox = new JCheckBox(NbBundle.getMessage(
                MergeRequestPanel.class, "MergeRequestPanel.squash"));
        removeSourceBranchCheckBox = new JCheckBox(NbBundle.getMessage(
                MergeRequestPanel.class, "MergeRequestPanel.removeSourceBranch"));
        pushFirstCheckBox = new JCheckBox(NbBundle.getMessage(
                MergeRequestPanel.class, "MergeRequestPanel.pushFirst"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;

        int row = 0;
        addLabel(gbc, row, "MergeRequestPanel.project");
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1;
        add(new JLabel(projectPath), gbc);
        row++;

        addLabel(gbc, row, "MergeRequestPanel.sourceBranch");
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1;
        add(sourceBranchCombo, gbc);
        row++;

        addLabel(gbc, row, "MergeRequestPanel.targetBranch");
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1;
        add(targetBranchCombo, gbc);
        row++;

        addLabel(gbc, row, "MergeRequestPanel.title");
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1;
        add(titleField, gbc);
        row++;

        addLabel(gbc, row, "MergeRequestPanel.description");
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        add(new JScrollPane(descriptionArea), gbc);
        row++;

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(createOptionsPanel(), gbc);

        DocumentListener documentListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                fireChange();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                fireChange();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                fireChange();
            }
        };
        titleField.getDocument().addDocumentListener(documentListener);
        editorDocument(sourceBranchCombo).addDocumentListener(documentListener);
        editorDocument(targetBranchCombo).addDocumentListener(documentListener);
    }

    private void addLabel(GridBagConstraints gbc, int row, String key) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        add(new JLabel(NbBundle.getMessage(MergeRequestPanel.class, key)), gbc);
    }

    private JPanel createOptionsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(draftCheckBox);
        panel.add(squashCheckBox);
        panel.add(removeSourceBranchCheckBox);
        panel.add(pushFirstCheckBox);
        return panel;
    }

    private static javax.swing.text.Document editorDocument(JComboBox<String> combo) {
        return ((JTextField) combo.getEditor().getEditorComponent()).getDocument();
    }

    private void fireChange() {
        changeSupport.fireChange();
    }

    boolean isValidForm() {
        String source = getSourceBranch();
        String target = getTargetBranch();
        return !source.isEmpty()
                && !target.isEmpty()
                && !source.equals(target)
                && !getTitle().isEmpty();
    }

    String getSourceBranch() {
        Object item = sourceBranchCombo.getEditor().getItem();
        return item == null ? "" : item.toString().trim();
    }

    String getTargetBranch() {
        Object item = targetBranchCombo.getEditor().getItem();
        return item == null ? "" : item.toString().trim();
    }

    String getTitle() {
        return titleField.getText().trim();
    }

    boolean isPushFirst() {
        return pushFirstCheckBox.isSelected();
    }

    MergeRequestRequest toRequest() {
        return new MergeRequestRequest(
                getSourceBranch(),
                getTargetBranch(),
                getTitle(),
                descriptionArea.getText().trim(),
                removeSourceBranchCheckBox.isSelected(),
                squashCheckBox.isSelected(),
                draftCheckBox.isSelected());
    }

    void addChangeListener(ChangeListener listener) {
        changeSupport.addChangeListener(listener);
    }

    void removeChangeListener(ChangeListener listener) {
        changeSupport.removeChangeListener(listener);
    }
}
