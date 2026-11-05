package dev.j8a.gitlab;

import org.junit.Test;
import org.openide.cookies.InstanceCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ActionRegistrationTest {

    @Test
    public void createMergeRequestActionIsRegistered() throws Exception {
        assertAction("Actions/Git/dev-j8a-gitlab-CreateMergeRequestAction.instance",
                "dev.j8a.gitlab.CreateMergeRequestAction");
        assertNotNull(FileUtil.getConfigFile(
                "Projects/Actions/dev-j8a-gitlab-CreateMergeRequestAction.shadow"));
    }

    @Test
    public void settingsActionIsRegistered() throws Exception {
        assertAction("Actions/Git/dev-j8a-gitlab-GitLabSettingsAction.instance",
                "dev.j8a.gitlab.GitLabSettingsAction");
    }

    private static void assertAction(String path, String className) throws Exception {
        FileObject file = FileUtil.getConfigFile(path);
        assertNotNull(path + " is missing", file);

        InstanceCookie cookie = DataObject.find(file)
                .getLookup().lookup(InstanceCookie.class);
        assertNotNull(path + " is not an instance", cookie);
        assertEquals(className, cookie.instanceClass().getName());
    }
}
