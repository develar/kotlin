/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.build.report

import org.jetbrains.kotlin.build.report.metrics.GradleBuildPerformanceMetric
import org.jetbrains.kotlin.build.report.metrics.GradleBuildTime
import org.jetbrains.kotlin.util.*

fun BuildReporter<GradleBuildTime, GradleBuildPerformanceMetric>.reportPerformanceData(moduleStats: UnitStats) {
    if (moduleStats.linesCount > 0) {
        addMetric(GradleBuildPerformanceMetric.SOURCE_LINES_NUMBER, moduleStats.linesCount.toLong())
    }

    fun reportLps(lpsMetrics: GradleBuildPerformanceMetric, time: Time) {
        if (time != Time.ZERO) {
            addMetric(lpsMetrics, moduleStats.getLinesPerSecond(time).toLong())
        }
    }

    var codegenTime: Time = Time.ZERO

    moduleStats.forEachPhaseMeasurement { phaseType, time ->
        if (time == null) return@forEachPhaseMeasurement

        val gradleBuildTime = when (phaseType) {
            PhaseType.Initialization -> GradleBuildTime.COMPILER_INITIALIZATION
            PhaseType.Analysis -> GradleBuildTime.CODE_ANALYSIS
            PhaseType.TranslationToIr -> GradleBuildTime.TRANSLATION_TO_IR
            PhaseType.IrLowering -> {
                codegenTime += time
                GradleBuildTime.IR_LOWERING
            }
            PhaseType.Backend -> {
                codegenTime += time
                GradleBuildTime.BACKEND
            }
        }

        addTimeMetricNs(gradleBuildTime, time.nanos)

        if (phaseType == PhaseType.Analysis) {
            reportLps(GradleBuildPerformanceMetric.ANALYSIS_LPS, time)
        }
    }

    if (codegenTime != Time.ZERO) {
        addTimeMetricNs(GradleBuildTime.CODE_GENERATION, codegenTime.nanos)
        reportLps(GradleBuildPerformanceMetric.CODE_GENERATION_LPS, codegenTime)
    }
}