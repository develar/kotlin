/*
 * Copyright 2010-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.plugin.project;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Key;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.Alarm;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.jps.model.JsExternalizationConstants;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.plugin.versions.KotlinLibrariesNotificationProvider;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class has utility functions to determine whether the project (or module) is js project.
 */
public final class JsModuleDetector {
    private static final AtomicInteger modificationCount = new AtomicInteger(1);
    private static final Key<Integer> IS_JS_MODULE = Key.create("IS_JS_MODULE");
    private static final Key<Boolean> PROJECT_LISTENER_ADDED = Key.create("kotlinJsModuleDetectorListenerAdded");

    private static final Alarm updateNotificationAlarm = new Alarm();

    private JsModuleDetector() {
    }

    public static boolean isJsModule(@NotNull final Module module) {
        if (module.getProject().getUserData(PROJECT_LISTENER_ADDED) == null) {
            module.getProject().putUserData(PROJECT_LISTENER_ADDED, true);
            addProjectRootsChangedListener(module.getProject());
        }

        Integer stamp = module.getUserData(IS_JS_MODULE);
        if (stamp != null && stamp == modificationCount.get()) {
            return true;
        }

        AccessToken token = ReadAction.start();
        try {
            final AtomicBoolean result = new AtomicBoolean();
            ModuleRootManager.getInstance(module).orderEntries().librariesOnly().forEachLibrary(new Processor<Library>() {
                @Override
                public boolean process(Library library) {
                    if (JsExternalizationConstants.JS_LIBRARY_NAME.equals(library.getName()) &&
                        KotlinLibrariesNotificationProvider.findLibraryFile(library, false) != null) {
                        module.putUserData(IS_JS_MODULE, modificationCount.get());
                        result.set(true);
                        return false;
                    }
                    return true;
                }
            });
            return result.get();
        }
        finally {
            token.finish();
        }
    }

    private static void addProjectRootsChangedListener(Project project) {
        project.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
            @Override
            public void rootsChanged(ModuleRootEvent event) {
                modificationCount.incrementAndGet();
                final Object source = event.getSource();
                if (source instanceof Project) {
                    updateNotificationAlarm.cancelAllRequests();
                    updateNotificationAlarm.addRequest(new Runnable() {
                        @Override
                        public void run() {
                            Project project = (Project) source;
                            if (!project.isDisposed()) {
                                EditorNotifications.getInstance(project).updateAllNotifications();
                            }
                        }
                    }, 300, ModalityState.NON_MODAL);
                }
            }
        });
    }

    public static boolean isJsModule(@NotNull JetFile file) {
        Module module = ModuleUtilCore.findModuleForPsiElement(file);
        return module != null && isJsModule(module);
    }
}
