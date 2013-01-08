package org.jetbrains.k2js;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.psi.JetFile;

import java.util.Collection;

public class Traverser {
    @NotNull
    public static final Key<String> MODULE_NAME_KEY = Key.create("externalModule");

    private Traverser() {
    }

    public static void addPsiFile(
            final Collection<JetFile> result,
            @Nullable final String moduleName,
            final PsiManager psiManager,
            final VirtualFile file
    ) {
        PsiFile psiFile = psiManager.findFile(file);
        assert psiFile != null;
        if (moduleName != null) {
            psiFile.putUserData(MODULE_NAME_KEY, moduleName);
        }
        result.add((JetFile) psiFile);
    }

    public static void traverseFile(
            Project project,
            VirtualFile file,
            final Collection<JetFile> result,
            @Nullable final String moduleName
    ) {
        final PsiManager psiManager = PsiManager.getInstance(project);
        VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                if (file.getName().endsWith(".kt")) {
                    addPsiFile(result, moduleName, psiManager, file);
                    return false;
                }
                return true;
            }
        });
    }
}
