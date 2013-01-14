package org.jetbrains.jet.plugin.build;

import com.intellij.compiler.impl.BuildTargetScopeProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerFilter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.jps.build.JsBuildTargetType;
import org.jetbrains.jet.plugin.packaging.JsCompilerOutputElementType;
import org.jetbrains.jet.plugin.packaging.JsModuleOutputPackagingElement;
import org.jetbrains.jet.plugin.project.JsModuleDetector;
import org.jetbrains.jps.api.CmdlineRemoteProto;

import java.util.Collections;
import java.util.List;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

class KotlinBuildTargetScopeProvider extends BuildTargetScopeProvider {
    @NotNull
    @Override
    public List<CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope> getBuildTargetScopes(
            @NotNull final CompileScope baseScope, @NotNull CompilerFilter filter, @NotNull final Project project
    ) {
        THashSet<Module> modules = new ReadAction<THashSet<Module>>() {
            @Override
            protected void run(Result<THashSet<Module>> result) {
                final THashSet<Module> modules = new THashSet<Module>();
                final PackagingElementResolvingContext context = ArtifactManager.getInstance(project).getResolvingContext();
                for (Artifact artifact : ArtifactCompileScope.getArtifactsToBuild(project, baseScope, false)) {
                    ArtifactUtil.processPackagingElements(artifact, JsCompilerOutputElementType.getInstance(),
                                                          new Processor<JsModuleOutputPackagingElement>() {
                                                              @Override
                                                              public boolean process(JsModuleOutputPackagingElement element) {
                                                                  Module module = element.findModule(context);
                                                                  // todo error report about unsuitable module
                                                                  if (module != null && JsModuleDetector.isJsModule(module)) {
                                                                      modules.add(module);
                                                                  }
                                                                  return true;
                                                              }
                                                          }, context, true);
                }
                result.setResult(modules);
            }
        }.execute().getResultObject();

        if (modules.isEmpty()) {
            return Collections.emptyList();
        }

        final TargetTypeBuildScope.Builder builder = TargetTypeBuildScope.newBuilder().setTypeId(JsBuildTargetType.TYPE_ID);
        modules.forEach(new TObjectProcedure<Module>() {
            @Override
            public boolean execute(Module module) {
                builder.addTargetId(module.getName());
                return true;
            }
        });
        return Collections.singletonList(builder.build());
    }
}
