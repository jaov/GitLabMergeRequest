package dev.j8a.gitlab;

import java.io.File;
import org.netbeans.api.project.Project;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;

/**
 * Resolves the project or file directory from the current action context.
 */
final class ProjectDirectoryFinder {

    private ProjectDirectoryFinder() {
    }

    static File find(Lookup context) {
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
}
