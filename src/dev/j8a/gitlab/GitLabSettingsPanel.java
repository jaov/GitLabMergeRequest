package dev.j8a.gitlab;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.util.ChangeSupport;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * Settings form for the GitLab host detected from the current Git remote.
 */
final class GitLabSettingsPanel extends JPanel {

    private static final RequestProcessor RP = new RequestProcessor(
            GitLabSettingsPanel.class);

    private record TokenCandidate(String token, String source) {
    }

    private final ChangeSupport changeSupport = new ChangeSupport(this);
    private final GitRemote remote;
    private final String host;
    private final JTextField baseUrlField = new JTextField();
    private final JPasswordField tokenField = new JPasswordField();
    private final JCheckBox useGitCredentialsCheckBox;
    private final JCheckBox clearTokenCheckBox;
    private final JButton testButton;
    private final JLabel testStatus = new JLabel();
    private final boolean tokenAlreadyStored;

    GitLabSettingsPanel(GitRemote remote) {
        super(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        this.remote = remote;
        host = remote.getHost();
        tokenAlreadyStored = GitLabSettings.hasToken(host);
        testButton = new JButton(NbBundle.getMessage(
                GitLabSettingsPanel.class, "GitLabSettingsPanel.test"));

        baseUrlField.setText(GitLabSettings.getBaseUrlOverride());
        baseUrlField.setToolTipText(NbBundle.getMessage(GitLabSettingsPanel.class,
                "GitLabSettingsPanel.baseUrlTooltip", remote.getBaseUrl()));

        useGitCredentialsCheckBox = new JCheckBox(NbBundle.getMessage(
                GitLabSettingsPanel.class, "GitLabSettingsPanel.useGitCredentials"));
        useGitCredentialsCheckBox.setSelected(GitLabSettings.getUseGitCredentials());

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

        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        add(useGitCredentialsCheckBox, gbc);
        row++;

        addLabel(gbc, row, "GitLabSettingsPanel.token");
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 1;
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
            row++;
        }

        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        JPanel testPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        testPanel.add(testButton);
        testPanel.add(Box.createHorizontalStrut(8));
        testPanel.add(testStatus);
        add(testPanel, gbc);

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
        useGitCredentialsCheckBox.addActionListener(
                event -> changeSupport.fireChange());
        clearTokenCheckBox.addActionListener(event -> changeSupport.fireChange());
        testButton.addActionListener(event -> testConnection());
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
        if (useGitCredentialsCheckBox.isSelected()) {
            return true;
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
        GitLabSettings.setUseGitCredentials(useGitCredentialsCheckBox.isSelected());
        if (clearTokenCheckBox.isSelected()) {
            GitLabSettings.setToken(host, null);
        } else if (!getToken().isEmpty()) {
            GitLabSettings.setToken(host, getToken());
        }
    }

    private void testConnection() {
        String typedToken = getToken();
        String storedToken = GitLabSettings.getToken(host);
        boolean useGitCredentials = useGitCredentialsCheckBox.isSelected();
        String baseUrl = getBaseUrlOverride().isEmpty()
                ? remote.getBaseUrl() : getBaseUrlOverride();
        String encodedProjectPath = remote.getEncodedProjectPath();

        testButton.setEnabled(false);
        testStatus.setText(NbBundle.getMessage(GitLabSettingsPanel.class,
                "GitLabSettingsPanel.testing"));

        RP.post(() -> {
            try {
                List<TokenCandidate> candidates = new ArrayList<>();
                if (!typedToken.isEmpty()) {
                    candidates.add(new TokenCandidate(typedToken,
                            NbBundle.getMessage(GitLabSettingsPanel.class,
                                    "GitLabSettingsPanel.customToken")));
                } else {
                    if (storedToken != null && !storedToken.isBlank()) {
                        candidates.add(new TokenCandidate(storedToken,
                                NbBundle.getMessage(GitLabSettingsPanel.class,
                                        "GitLabSettingsPanel.savedToken")));
                    }
                    if (useGitCredentials) {
                        for (GitCredentialReader.Credential credential
                                : GitCredentialReader.readCandidates(remote)) {
                            candidates.add(new TokenCandidate(credential.token(),
                                    sourceLabel(credential.source())));
                        }
                    }
                }

                if (candidates.isEmpty()) {
                    setTestStatus(NbBundle.getMessage(GitLabSettingsPanel.class,
                            "GitLabSettingsPanel.noCredential"));
                    return;
                }

                GitLabClientException lastApiError = null;
                for (TokenCandidate candidate : candidates) {
                    try {
                        GitLabClient client = new GitLabClient(baseUrl,
                                candidate.token());
                        GitLabProject project = client.getProject(
                                encodedProjectPath);
                        setTestStatus(NbBundle.getMessage(
                                GitLabSettingsPanel.class,
                                "GitLabSettingsPanel.connected",
                                project.getPathWithNamespace(),
                                candidate.source()));
                        return;
                    } catch (GitLabClientException ex) {
                        lastApiError = ex;
                        if (ex.getStatusCode() != 401
                                && ex.getStatusCode() != 403) {
                            setTestStatus(apiError(ex));
                            return;
                        }
                    } catch (IOException ex) {
                        setTestStatus(NbBundle.getMessage(
                                GitLabSettingsPanel.class,
                                "GitLabSettingsPanel.connectionError",
                                ex.getMessage()));
                        return;
                    }
                }

                if (lastApiError != null) {
                    setTestStatus(apiError(lastApiError));
                } else {
                    setTestStatus(NbBundle.getMessage(GitLabSettingsPanel.class,
                            "GitLabSettingsPanel.noCredential"));
                }
            } catch (RuntimeException ex) {
                setTestStatus(NbBundle.getMessage(GitLabSettingsPanel.class,
                        "GitLabSettingsPanel.connectionError",
                        ex.getMessage()));
            }
        });
    }

    private static String apiError(GitLabClientException exception) {
        String message = NbBundle.getMessage(GitLabSettingsPanel.class,
                "GitLabSettingsPanel.apiError",
                exception.getStatusCode(), exception.getMessage());
        if (exception.getStatusCode() == 403) {
            return NbBundle.getMessage(GitLabSettingsPanel.class,
                    "GitLabSettingsPanel.scopeHint", message);
        }
        return message;
    }

    private static String sourceLabel(GitCredentialReader.Source source) {
        if (source == GitCredentialReader.Source.GIT_HELPER) {
            return NbBundle.getMessage(GitLabSettingsPanel.class,
                    "GitLabSettingsPanel.source.gitHelper");
        }
        return NbBundle.getMessage(GitLabSettingsPanel.class,
                "GitLabSettingsPanel.source.netbeansKeyring");
    }

    private void setTestStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            testStatus.setText(message);
            testButton.setEnabled(true);
        });
    }

    void addChangeListener(ChangeListener listener) {
        changeSupport.addChangeListener(listener);
    }

    void removeChangeListener(ChangeListener listener) {
        changeSupport.removeChangeListener(listener);
    }
}
