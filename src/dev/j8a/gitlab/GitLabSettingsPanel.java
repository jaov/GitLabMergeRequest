package dev.j8a.gitlab;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.util.ChangeSupport;
import org.openide.util.NbBundle;

/**
 * Settings form for the GitLab host detected from the current Git remote.
 */
final class GitLabSettingsPanel extends JPanel {

    private final ChangeSupport changeSupport = new ChangeSupport(this);
    private final String host;
    private final JTextField baseUrlField = new JTextField();
    private final JPasswordField tokenField = new JPasswordField();
    private final JCheckBox clearTokenCheckBox;
    private final boolean tokenAlreadyStored;

    GitLabSettingsPanel(GitRemote remote) {
        super(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        host = remote.getHost();
        tokenAlreadyStored = GitLabSettings.hasToken(host);

        baseUrlField.setText(GitLabSettings.getBaseUrlOverride());
        baseUrlField.setToolTipText(NbBundle.getMessage(GitLabSettingsPanel.class,
                "GitLabSettingsPanel.baseUrlTooltip", remote.getBaseUrl()));

        clearTokenCheckBox = new JCheckBox(NbBundle.getMessage(
                GitLabSettingsPanel.class, "GitLabSettingsPanel.clearToken"));
        clearTokenCheckBox.setVisible(tokenAlreadyStored);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;

        int row = 0;
        addLabel(gbc, row, "GitLabSettingsPanel.host");
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1;
        add(new JLabel(host), gbc);
        row++;

        addLabel(gbc, row, "GitLabSettingsPanel.baseUrl");
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1;
        add(baseUrlField, gbc);
        row++;

        addLabel(gbc, row, "GitLabSettingsPanel.token");
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1;
        add(tokenField, gbc);
        row++;

        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        add(new JLabel(tokenAlreadyStored
                ? NbBundle.getMessage(GitLabSettingsPanel.class,
                        "GitLabSettingsPanel.tokenStored")
                : NbBundle.getMessage(GitLabSettingsPanel.class,
                        "GitLabSettingsPanel.tokenRequired")), gbc);
        row++;

        if (tokenAlreadyStored) {
            gbc.gridx = 1;
            gbc.gridy = row;
            gbc.gridwidth = 2;
            add(clearTokenCheckBox, gbc);
        }

        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                changeSupport.fireChange();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                changeSupport.fireChange();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                changeSupport.fireChange();
            }
        };
        baseUrlField.getDocument().addDocumentListener(listener);
        tokenField.getDocument().addDocumentListener(listener);
        clearTokenCheckBox.addActionListener(event -> changeSupport.fireChange());
    }

    private void addLabel(GridBagConstraints gbc, int row, String key) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        add(new JLabel(NbBundle.getMessage(GitLabSettingsPanel.class, key)), gbc);
    }

    boolean isValidForm() {
        String baseUrl = getBaseUrlOverride();
        if (!baseUrl.isEmpty()
                && !baseUrl.startsWith("http://")
                && !baseUrl.startsWith("https://")) {
            return false;
        }
        if (clearTokenCheckBox.isSelected()) {
            return true;
        }
        if (!getToken().isEmpty()) {
            return true;
        }
        return tokenAlreadyStored;
    }

    String getBaseUrlOverride() {
        return baseUrlField.getText().trim();
    }

    String getToken() {
        return new String(tokenField.getPassword()).trim();
    }

    void save() {
        GitLabSettings.setBaseUrlOverride(getBaseUrlOverride());
        if (clearTokenCheckBox.isSelected()) {
            GitLabSettings.setToken(host, null);
        } else if (!getToken().isEmpty()) {
            GitLabSettings.setToken(host, getToken());
        }
    }

    void addChangeListener(ChangeListener listener) {
        changeSupport.addChangeListener(listener);
    }

    void removeChangeListener(ChangeListener listener) {
        changeSupport.removeChangeListener(listener);
    }
}
