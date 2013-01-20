/*
 * Copyright 2010-2012 JetBrains s.r.o.
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

package org.jetbrains.jet.compiler.runner;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.lang.UrlClassLoader;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.cli.common.messages.CompilerMessageLocation;
import org.jetbrains.jet.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.jet.cli.common.messages.MessageCollector;
import org.jetbrains.jet.config.CompilerConfiguration;
import org.jetbrains.jet.config.CompilerConfigurationKey;
import org.jetbrains.jet.utils.KotlinPaths;
import org.jetbrains.kotlin.compiler.CompilerConfigurationKeys;
import org.jetbrains.kotlin.compiler.JsCompilerConfigurationKeys;
import org.jetbrains.kotlin.compiler.ModuleInfoProvider;

import java.io.*;
import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.jet.cli.common.messages.CompilerMessageLocation.NO_LOCATION;
import static org.jetbrains.jet.cli.common.messages.CompilerMessageSeverity.ERROR;

public class CompilerRunnerUtil {
    private static final Object classLoaderRefLock = new Object();
    private static volatile SoftReference<ClassLoader> classLoaderRef = new SoftReference<ClassLoader>(null);

    private CompilerRunnerUtil() {
    }

    public static List<File> kompilerClasspath(KotlinPaths paths, MessageCollector messageCollector) {
        List<File> result = new SmartList<File>();
        String classpath = System.getProperty("kotlin.compiler.classpath");
        if (classpath != null) {
            for (String path : StringUtil.split(classpath, ";")) {
                result.add(new File(path));
            }
            return result;
        }

        File libs = paths.getLibPath();
        if (!libs.exists() || libs.isFile()) {
            messageCollector.report(ERROR, "Broken compiler at '" + libs.getAbsolutePath() + "'. Make sure plugin is properly installed", NO_LOCATION);
            return Collections.emptyList();
        }

        result.add(new File(libs, "kotlin-compiler.jar"));
        return result;
    }

    public static ClassLoader getOrCreateClassLoader(KotlinPaths paths, MessageCollector messageCollector) {
        ClassLoader result = classLoaderRef.get();
        if (result != null) {
            return result;
        }

        synchronized (classLoaderRefLock) {
            result = classLoaderRef.get();
            if (result == null) {
                result = createClassLoader(paths, messageCollector);
                classLoaderRef = new SoftReference<ClassLoader>(result);
            }
        }

        return result;
    }

    private static UrlClassLoader createClassLoader(KotlinPaths paths, MessageCollector messageCollector) {
        List<File> jars = kompilerClasspath(paths, messageCollector);
        URL[] urls = new URL[jars.size()];
        for (int i = 0; i < urls.length; i++) {
            try {
                urls[i] = jars.get(i).toURI().toURL();
            }
            catch (MalformedURLException e) {
                throw new RuntimeException(e); // Checked exceptions are great! I love them, and I love brilliant library designers too!
            }
        }
        return new MyUrlClassLoader(urls);
    }

    static void handleProcessTermination(int exitCode, MessageCollector messageCollector) {
        if (exitCode != 0 && exitCode != 1) {
            messageCollector.report(ERROR, "Compiler terminated with exit code: " + exitCode, NO_LOCATION);
        }
    }

    public static int getReturnCodeFromObject(Object rc) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        if ("org.jetbrains.jet.cli.common.ExitCode".equals(rc.getClass().getCanonicalName())) {
            return (Integer)rc.getClass().getMethod("getCode").invoke(rc);
        }
        else {
            throw new IllegalStateException("Unexpected return: " + rc);
        }
    }

    public static Object invokeExecMethod(CompilerEnvironment environment,
            PrintStream out,
            MessageCollector messageCollector, String[] arguments, String name) throws Exception {
        ClassLoader loader = getOrCreateClassLoader(environment.getKotlinPaths(), messageCollector);
        Class<?> kompiler = Class.forName(name, true, loader);
        Method exec = kompiler.getMethod("exec", PrintStream.class, String[].class);
        return exec.invoke(kompiler.newInstance(), out, arguments);
    }

    public static void outputCompilerMessagesAndHandleExitCode(@NotNull MessageCollector messageCollector,
            @NotNull OutputItemsCollector outputItemsCollector,
            @NotNull Function<PrintStream, Integer> compilerRun) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outputStream);

        int exitCode = compilerRun.fun(out);

        BufferedReader reader = new BufferedReader(new StringReader(outputStream.toString()));
        CompilerOutputParser.parseCompilerMessagesFromReader(messageCollector, reader, outputItemsCollector);
        handleProcessTermination(exitCode, messageCollector);
    }

    private static final class MyUrlClassLoader extends UrlClassLoader {
        private final THashMap<String, Class> sharedClassesMap;

        public MyUrlClassLoader(URL[] urls) {
            super(urls, null);

            Class<?>[] sharedClasses = {CompilerConfiguration.class, CompilerConfigurationKey.class, CompilerConfigurationKeys.class,
                    ModuleInfoProvider.class, ModuleInfoProvider.DependenciesProcessor.class, Processor.class,
                    MessageCollector.class, CompilerMessageSeverity.class, CompilerMessageLocation.class, JsCompilerConfigurationKeys.class,
                    Consumer.class};
            sharedClassesMap = new THashMap<String, Class>(sharedClasses.length);
            for (Class sharedClass : sharedClasses) {
                sharedClassesMap.put(sharedClass.getName(), sharedClass);
            }
        }

        @NotNull
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            Class sharedClass = sharedClassesMap.get(name);
            return sharedClass != null ? sharedClass : super.findClass(name);
        }
    }
}
