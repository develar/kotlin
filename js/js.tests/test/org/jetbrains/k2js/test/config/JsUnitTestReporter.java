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

package org.jetbrains.k2js.test.config;

import com.google.common.collect.Lists;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashMap;
import junit.framework.Assert;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class JsUnitTestReporter {
    @NotNull
    private final Map<String, Boolean> finishedTests = new THashMap<String, Boolean>();
    @NotNull
    private final MultiMap<String, String> errors = MultiMap.createSmartList();
    @NotNull
    private final List<String> processedTests = new ArrayList<String>();

    @Nullable
    private String currentTestName;

    //NOTE: usable from Rhino
    @SuppressWarnings("UnusedDeclaration")
    public void testStart(@NotNull String testName) {
        assert currentTestName == null;
        currentTestName = testName;
    }

    //NOTE: usable from Rhino
    @SuppressWarnings("UnusedDeclaration")
    public void testFail(@NotNull String testName) {
        assert currentTestName != null;
        finishedTests.put(testName, false);
        currentTestName = null;
    }

    //NOTE: usable from Rhino
    @SuppressWarnings("UnusedDeclaration")
    public void testSuccess(@NotNull String testName) {
        assert currentTestName != null;
        finishedTests.put(testName, true);
        currentTestName = null;
    }

    //NOTE: usable from Rhino
    @SuppressWarnings("UnusedDeclaration")
    public void reportError(@NotNull String message) {
        Collection<String> errorList = errors.get(currentTestName);
        if (errorList.isEmpty()) {
            errors.putValue(currentTestName, message);
        }
        else {
            errorList.add(message);
        }
    }

    @NotNull
    private Collection<String> getNewFinishedTests() {
        Collection<String> finishedTests = Lists.newArrayList(this.finishedTests.keySet());
        finishedTests.removeAll(processedTests);
        return finishedTests;
    }

    @NotNull
    public Collection<String> getErrors(@NotNull String testName) {
        Collection<String> errorList = errors.get(testName);
        assert !errorList.isEmpty();
        return errorList;
    }

    @NotNull
    public Boolean getResult(@NotNull String testName) {
        Boolean result = finishedTests.get(testName);
        assert result != null;
        return result;
    }

    @NotNull
    public TestSuite createTestSuiteAndFlush() {
        TestSuite suite = new TestSuite("!");
        Collection<String> newFinishedTests = getNewFinishedTests();
        for (String test : newFinishedTests) {
            //NOTE: well, it is a test
            //noinspection JUnitTestCaseWithNoTests
            suite.addTest(new TestCase(test) {
                @Override
                protected void runTest() throws Throwable {
                    Boolean result = getResult(getName());
                    if (!result) {
                        Collection<String> errorMessages = getErrors(getName());
                        StringBuilder sb = new StringBuilder();
                        for (String error : errorMessages) {
                            sb.append(error);
                        }
                        eraseTestInfo(getName());
                        Assert.fail(sb.toString());
                    }
                    eraseTestInfo(getName());
                    Assert.assertTrue(result);
                }
            });
        }
        processedTests.addAll(newFinishedTests);
        return suite;
    }

    private void eraseTestInfo(@NotNull String testName) {
        errors.remove(testName);
        Boolean removed = finishedTests.remove(testName);
        assert removed != null;
    }

    public void ignoreTests(@NotNull String... testNames) {
        for (String testName : testNames) {
            if (getResult(testName)) {
                //TODO: make test fail?
                //noinspection UseOfSystemOutOrSystemErr
                System.out.println("Test " + testName + " has passed. And we ignored it! Turn it on?");
            }
            eraseTestInfo(testName);
        }
    }
}
