package org.jetbrains.jet.plugin.debugger;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairProcessor;
import org.jetbrains.jet.plugin.JetFileType;
import org.jetbrains.jet.plugin.project.K2JSModuleComponent;

public class KotlinJavaScriptBreakpointAware implements PairProcessor<VirtualFile, Project> {
    @Override
    public boolean process(VirtualFile file, Project project) {
        if (file.getFileType() == JetFileType.INSTANCE) {
            Module module = ModuleUtilCore.findModuleForFile(file, project);
            if (module != null) {
                return K2JSModuleComponent.getInstance(module).isJavaScriptModule();
            }
        }
        return false;
    }
}
