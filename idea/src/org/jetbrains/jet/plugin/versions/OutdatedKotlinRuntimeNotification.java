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

package org.jetbrains.jet.plugin.versions;

import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.intellij.framework.library.LibraryVersionProperties;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.plugin.JetPluginUtil;
import org.jetbrains.jet.plugin.framework.JavaRuntimePresentationProvider;
import org.jetbrains.jet.plugin.framework.LibraryPresentationProviderUtil;
import org.jetbrains.jet.utils.PathUtil;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.util.Collection;
import java.util.List;

public class OutdatedKotlinRuntimeNotification implements StartupActivity, DumbAware {
    private static final String SUPPRESSED_PROPERTY_NAME = "oudtdated.runtime.suppressed.plugin.version";

    @Override
    public void runActivity(Project project) {
        projectOpened(project);
    }

    private static class VersionedLibrary extends Pair<Library, String> {
        public VersionedLibrary(@NotNull Library library, @Nullable String version) {
            super(library, version);
        }

        @NotNull
        public Library getLibrary() {
            return first;
        }

        @Nullable
        public String getVersion() {
            return second;
        }
    }

    private static void projectOpened(final Project myProject) {
        if (ApplicationManager.getApplication().isInternal()) return;

        StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
            @Override
            public void run() {
                final String pluginVersion = JetPluginUtil.getPluginVersion();
                if ("@snapshot@".equals(pluginVersion)) return; // plugin is run from sources, can't compare versions

                // user already clicked suppress
                if (pluginVersion.equals(PropertiesComponent.getInstance(myProject).getValue(SUPPRESSED_PROPERTY_NAME))) return;

                Collection<VersionedLibrary> versionedOutdatedLibraries = findOutdatedKotlinLibraries(myProject, pluginVersion);
                if (versionedOutdatedLibraries.isEmpty()) {
                    return;
                }

                final Collection<Library> outdatedLibraries = extractLibraries(versionedOutdatedLibraries);

                String message;
                if (versionedOutdatedLibraries.size() == 1) {
                    VersionedLibrary versionedLibrary = versionedOutdatedLibraries.iterator().next();

                    String version = versionedLibrary.getVersion();
                    String readableVersion = version == null ? "unknown" : version;
                    String libraryName = versionedLibrary.getLibrary().getName();

                    message = String.format(
                            "<p>Your version of Kotlin runtime in '%s' library is %s, while plugin version is %s.</p>" +
                            "<p>Runtime library should be updated to avoid compatibility problems.</p>" +
                            "<p><a href=\"update\">Update Runtime</a> <a href=\"ignore\">Ignore</a></p>",
                            libraryName,
                            readableVersion,
                            pluginVersion);
                }
                else {
                    String libraryNames = StringUtil.join(outdatedLibraries, new Function<Library, String>() {
                        @Override
                        public String fun(Library library) {
                            return library.getName();
                        }
                    }, ", ");

                    message = String.format(
                            "<p>Version of Kotlin runtime is outdated in several libraries (%s). Plugin version is %s.</p>" +
                            "<p>Runtime libraries should be updated to avoid compatibility problems.</p>" +
                            "<p><a href=\"update\">Update All</a> <a href=\"ignore\">Ignore</a></p>",
                            libraryNames,
                            pluginVersion);
                }


                Notifications.Bus.notify(new Notification("Outdated Kotlin Runtime", "Outdated Kotlin Runtime", message,
                                                          NotificationType.WARNING, new NotificationListener() {
                    @Override
                    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
                        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                            if ("update".equals(event.getDescription())) {
                                updateKotlinLibraries(outdatedLibraries, showRuntimeJarNotFoundDialog(myProject));
                            }
                            else if ("ignore".equals(event.getDescription())) {
                                PropertiesComponent.getInstance(myProject).setValue(SUPPRESSED_PROPERTY_NAME, pluginVersion);
                            }
                            else {
                                throw new AssertionError();
                            }
                            notification.expire();
                        }
                    }
                }), myProject);
            }
        });
    }

    private static Collection<Library> extractLibraries(Collection<VersionedLibrary> libraries) {
        return Collections2.transform(libraries, new com.google.common.base.Function<VersionedLibrary, Library>() {
            @Override
            public Library apply(@Nullable VersionedLibrary versionedLibrary) {
                assert versionedLibrary != null;
                return versionedLibrary.getLibrary();
            }
        });
    }

    private static void updateKotlinLibraries(Collection<Library> libraries, @NotNull Runnable jarNotFoundHandler) {
        File runtimePath = PathUtil.getKotlinPathsForIdeaPlugin().getRuntimePath();
        if (!runtimePath.exists()) {
            jarNotFoundHandler.run();
            return;
        }

        for (Library library : libraries) {
            VirtualFile libraryJar = JavaRuntimePresentationProvider.getRuntimeJar(library);
            assert LibraryPresentationProviderUtil.isDetected(JavaRuntimePresentationProvider.getInstance(), library) &&
                   libraryJar != null : "Only java runtime libraries are expected";

            KotlinRuntimeLibraryUtil.replaceFile(runtimePath, libraryJar);
        }
    }

    private static Collection<VersionedLibrary> findOutdatedKotlinLibraries(Project project, String pluginVersion) {
        List<VersionedLibrary> outdatedLibraries = Lists.newArrayList();

        for (VersionedLibrary versionedLibrary : findKotlinLibraries(project)) {
            String libraryVersion = versionedLibrary.getVersion();

            boolean isOutdated = "snapshot".equals(libraryVersion)
                                        || libraryVersion == null
                                        || libraryVersion.startsWith("internal-") != pluginVersion.startsWith("internal-")
                                        || VersionComparatorUtil.compare(pluginVersion, libraryVersion) > 0;

            if (isOutdated) {
                outdatedLibraries.add(versionedLibrary);
            }
        }

        return outdatedLibraries;
    }

    private static Collection<VersionedLibrary> findKotlinLibraries(Project project) {
        List<VersionedLibrary> libraries = Lists.newArrayList();

        for (Module module : ModuleManager.getInstance(project).getModules()) {
            ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

            for (OrderEntry entry : moduleRootManager.getOrderEntries()) {
                if (entry instanceof LibraryOrderEntry) {
                    LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry) entry;
                    Library library = libraryOrderEntry.getLibrary();

                    if (library == null) {
                        continue;
                    }

                    LibraryVersionProperties javaRuntimeProperties = LibraryPresentationProviderUtil.getLibraryProperties(
                            JavaRuntimePresentationProvider.getInstance(), library);
                    if (javaRuntimeProperties != null) {
                        libraries.add(new VersionedLibrary(library, javaRuntimeProperties.getVersionString()));
                    }

                    // TODO: search js libraries as well
                }
            }
        }

        return libraries;
    }

    @NotNull
    public static Runnable showRuntimeJarNotFoundDialog(@NotNull final Project project) {
        return new Runnable() {
            @Override
            public void run() {
                Messages.showErrorDialog(project,
                                         "kotlin-runtime.jar is not found. Make sure plugin is properly installed.",
                                         "No Runtime Found");
            }
        };
    }
}
