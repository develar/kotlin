package org.jetbrains.k2js;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.JetFile;

import java.util.Collection;

public class Traverser {
    private Traverser() {
    }

    public static void addPsiFile(
            Collection<JetFile> result,
            PsiManager psiManager,
            VirtualFile file
    ) {
        PsiFile psiFile = psiManager.findFile(file);
        assert psiFile != null;
        result.add((JetFile) psiFile);
    }

    public static void traverseFile(
            Project project,
            VirtualFile file,
            final Collection<JetFile> result
    ) {
        final PsiManager psiManager = PsiManager.getInstance(project);
        VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                if (file.getName().endsWith(".kt")) {
                    addPsiFile(result, psiManager, file);
                    return false;
                }
                return true;
            }
        });
    }
}
