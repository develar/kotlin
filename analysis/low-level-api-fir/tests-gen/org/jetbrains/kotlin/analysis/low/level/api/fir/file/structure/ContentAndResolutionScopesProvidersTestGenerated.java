/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.file.structure;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link org.jetbrains.kotlin.generators.tests.analysis.api.GenerateAnalysisApiTestsKt}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("analysis/low-level-api-fir/testData/contentAndResolutionScopesProviders")
@TestDataPath("$PROJECT_ROOT")
public class ContentAndResolutionScopesProvidersTestGenerated extends AbstractContentAndResolutionScopesProvidersTest {
  @Test
  public void testAllFilesPresentInContentAndResolutionScopesProviders() {
    KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("analysis/low-level-api-fir/testData/contentAndResolutionScopesProviders"), Pattern.compile("^([^.]+)\\.kt$"), null, false);
  }

  @Test
  @TestMetadata("empty.kt")
  public void testEmpty() {
    runTest("analysis/low-level-api-fir/testData/contentAndResolutionScopesProviders/empty.kt");
  }

  @Test
  @TestMetadata("libraryDependency.kt")
  public void testLibraryDependency() {
    runTest("analysis/low-level-api-fir/testData/contentAndResolutionScopesProviders/libraryDependency.kt");
  }

  @Test
  @TestMetadata("librarySourceWithFallbackDependencies.kt")
  public void testLibrarySourceWithFallbackDependencies() {
    runTest("analysis/low-level-api-fir/testData/contentAndResolutionScopesProviders/librarySourceWithFallbackDependencies.kt");
  }

  @Test
  @TestMetadata("librarySourceWithRegularDependencies.kt")
  public void testLibrarySourceWithRegularDependencies() {
    runTest("analysis/low-level-api-fir/testData/contentAndResolutionScopesProviders/librarySourceWithRegularDependencies.kt");
  }

  @Test
  @TestMetadata("randomFiles.kt")
  public void testRandomFiles() {
    runTest("analysis/low-level-api-fir/testData/contentAndResolutionScopesProviders/randomFiles.kt");
  }

  @Test
  @TestMetadata("shadowedAndAdded.kt")
  public void testShadowedAndAdded() {
    runTest("analysis/low-level-api-fir/testData/contentAndResolutionScopesProviders/shadowedAndAdded.kt");
  }

  @Test
  @TestMetadata("shadowingInsideSingleModule.kt")
  public void testShadowingInsideSingleModule() {
    runTest("analysis/low-level-api-fir/testData/contentAndResolutionScopesProviders/shadowingInsideSingleModule.kt");
  }

  @Test
  @TestMetadata("singleFile.kt")
  public void testSingleFile() {
    runTest("analysis/low-level-api-fir/testData/contentAndResolutionScopesProviders/singleFile.kt");
  }

  @Test
  @TestMetadata("singleModule.kt")
  public void testSingleModule() {
    runTest("analysis/low-level-api-fir/testData/contentAndResolutionScopesProviders/singleModule.kt");
  }
}
