/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir.transformers

import org.jetbrains.kotlin.analysis.low.level.api.fir.api.targets.LLFirResolveTarget
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.throwUnexpectedFirElementError
import org.jetbrains.kotlin.analysis.low.level.api.fir.lazy.resolve.FirLazyBodiesCalculator
import org.jetbrains.kotlin.analysis.low.level.api.fir.lazy.resolve.NonLocalAnnotationVisitor
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.llFirSession
import org.jetbrains.kotlin.analysis.low.level.api.fir.util.checkAnnotationsAreResolved
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirElementWithResolveState
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.canHaveDeferredReturnTypeCalculation
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirEmptyArgumentList
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.impl.FirResolvedArgumentList
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.isError
import org.jetbrains.kotlin.fir.resolve.ResolutionMode
import org.jetbrains.kotlin.fir.resolve.transformers.plugin.FirAnnotationArgumentsTransformer
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.lazyResolveToPhase
import org.jetbrains.kotlin.fir.types.FirTypeProjection
import org.jetbrains.kotlin.fir.visitors.transformSingle

internal object LLFirAnnotationArgumentsLazyResolver : LLFirLazyResolver(FirResolvePhase.ANNOTATION_ARGUMENTS) {
    override fun createTargetResolver(target: LLFirResolveTarget): LLFirTargetResolver = LLFirAnnotationArgumentsTargetResolver(target)

    override fun phaseSpecificCheckIsResolved(target: FirElementWithResolveState) {
        if (target is FirAnnotationContainer) {
            checkAnnotationsAreResolved(target)
        }

        when (target) {
            is FirCallableDeclaration -> checkAnnotationsAreResolved(target, target.returnTypeRef)
            is FirReceiverParameter -> checkAnnotationsAreResolved(target, target.typeRef)
            is FirTypeParameter -> {
                for (bound in target.bounds) {
                    checkAnnotationsAreResolved(target, bound)
                }
            }

            is FirRegularClass -> {
                for (typeRef in target.superTypeRefs) {
                    checkAnnotationsAreResolved(target, typeRef)
                }
            }

            is FirTypeAlias -> checkAnnotationsAreResolved(target, target.expandedTypeRef)
        }
    }
}

/**
 * This resolver is responsible for [ANNOTATION_ARGUMENTS][FirResolvePhase.ANNOTATION_ARGUMENTS] phase.
 *
 * This resolver:
 * - Transforms unresolved annotation arguments into resolved ones.
 *   It includes both regular and type annotations.
 *
 * Before the transformation, the resolver [recreates][AnnotationArgumentsStateKeepers] all unresolved argument lists
 * to prevent corrupted states due to [PCE][com.intellij.openapi.progress.ProcessCanceledException].
 *
 * @see postponedSymbolsForAnnotationResolution
 * @see AnnotationArgumentsStateKeepers
 * @see FirAnnotationArgumentsTransformer
 * @see FirResolvePhase.ANNOTATION_ARGUMENTS
 */
private class LLFirAnnotationArgumentsTargetResolver(resolveTarget: LLFirResolveTarget) : LLFirAbstractBodyTargetResolver(
    resolveTarget,
    FirResolvePhase.ANNOTATION_ARGUMENTS,
) {
    /**
     * All foreign annotations have to be resolved before by [postponedSymbolsForAnnotationResolution] or [resolveDependencies]
     * so there is no sense to override
     * [transformForeignAnnotationCall][org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirAbstractBodyResolveTransformerDispatcher.transformForeignAnnotationCall]
     *
     * We can add additional [checkAnnotationCallIsResolved][org.jetbrains.kotlin.analysis.low.level.api.fir.util.checkAnnotationCallIsResolved],
     * but this also doesn't make sense
     * because we anyway will check all annotations during [LLFirAnnotationArgumentsLazyResolver.phaseSpecificCheckIsResolved]
     *
     * @see postponedSymbolsForAnnotationResolution
     * @see org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirAbstractBodyResolveTransformerDispatcher.transformForeignAnnotationCall
     */
    override val transformer = FirAnnotationArgumentsTransformer(
        resolveTargetSession,
        resolveTargetScopeSession,
        resolverPhase,
        returnTypeCalculator = createReturnTypeCalculator(),
    )

    override fun doResolveWithoutLock(target: FirElementWithResolveState): Boolean {
        if (target !is FirDeclaration) return false

        var processed = false
        var symbolsToResolve: Collection<FirBasedSymbol<*>>? = null
        withReadLock(target) {
            processed = true
            symbolsToResolve = buildList {
                target.forEachDeclarationWhichCanHavePostponedSymbols {
                    addAll(it.postponedSymbolsForAnnotationResolution.orEmpty())
                }

                addSymbolsFromForeignAnnotations(target)
            }
        }

        // some other thread already resolved this element to this or upper phase
        if (!processed) return true
        symbolsToResolve?.forEach { it.lazyResolveToPhase(resolverPhase) }

        return false
    }

    private fun MutableList<FirBasedSymbol<*>>.addSymbolsFromForeignAnnotations(target: FirDeclaration) {
        // It is fine to just visit the declaration recursively as copy declarations don't have a body
        target.accept(ForeignAnnotationsCollector, ForeignAnnotationsContext(this, target.symbol))
    }

    private class ForeignAnnotationsContext(val collection: MutableCollection<FirBasedSymbol<*>>, val currentSymbol: FirBasedSymbol<*>)
    private object ForeignAnnotationsCollector : NonLocalAnnotationVisitor<ForeignAnnotationsContext>() {
        override fun processAnnotation(annotation: FirAnnotation, data: ForeignAnnotationsContext) {
            if (annotation !is FirAnnotationCall) return
            val symbolToPostpone = annotation.containingDeclarationSymbol.symbolToPostponeIfCanBeResolvedOnDemand() ?: return
            if (symbolToPostpone != data.currentSymbol) {
                data.collection += symbolToPostpone
            }
        }
    }

    override fun doLazyResolveUnderLock(target: FirElementWithResolveState) {
        // There is no sense to resolve such declarations as they do not have their own annotations
        if (target is FirCallableDeclaration && target.canHaveDeferredReturnTypeCalculation) return

        resolveWithKeeper(
            target,
            target.llFirSession,
            AnnotationArgumentsStateKeepers.DECLARATION,
            prepareTarget = FirLazyBodiesCalculator::calculateAnnotations,
        ) {
            transformAnnotations(target)
        }

        if (target is FirDeclaration) {
            /**
             * All symbols from [postponedSymbolsForAnnotationResolution] already processed during [doResolveWithoutLock],
             * so we have to clean up the attribute
             */
            target.forEachDeclarationWhichCanHavePostponedSymbols {
                it.postponedSymbolsForAnnotationResolution = null
            }
        }
    }

    @Deprecated("Should never be called directly, only for override purposes, please use withRegularClass", level = DeprecationLevel.ERROR)
    override fun withContainingRegularClass(firClass: FirRegularClass, action: () -> Unit) {
        transformer.declarationsTransformer.forRegularClassBody(firClass) {
            action()
            firClass
        }
    }

    private fun transformAnnotations(target: FirElementWithResolveState) {
        when (target) {
            is FirRegularClass -> {
                val declarationTransformer = transformer.declarationsTransformer
                declarationTransformer.context.withClassHeader(target) {
                    target.transformAnnotations(declarationTransformer, ResolutionMode.ContextIndependent)
                    target.transformTypeParameters(declarationTransformer, ResolutionMode.ContextIndependent)
                    target.transformSuperTypeRefs(declarationTransformer, ResolutionMode.ContextIndependent)
                    target.contextParameters.forEach { it.transformSingle(declarationTransformer, ResolutionMode.ContextIndependent) }
                }
            }

            is FirScript -> target.transformAnnotations(transformer.declarationsTransformer, ResolutionMode.ContextIndependent)
            is FirFile -> transformer.declarationsTransformer.withFile(target) {
                target.transformAnnotations(transformer.declarationsTransformer, ResolutionMode.ContextIndependent)
            }

            is FirCallableDeclaration, is FirAnonymousInitializer, is FirDanglingModifierList, is FirTypeAlias -> {
                target.transformSingle(transformer, ResolutionMode.ContextIndependent)
            }

            is FirCodeFragment -> {}
            else -> throwUnexpectedFirElementError(target)
        }
    }
}

internal object AnnotationArgumentsStateKeepers {
    private val ANNOTATION: StateKeeper<FirAnnotation, FirSession> = stateKeeper { builder, _, session ->
        builder.add(ANNOTATION_BASE, session)
        builder.add(FirAnnotation::argumentMapping, FirAnnotation::replaceArgumentMapping)
        builder.add(FirAnnotation::typeArgumentsCopied, FirAnnotation::replaceTypeArguments)
    }

    private val ANNOTATION_BASE: StateKeeper<FirAnnotation, FirSession> = stateKeeper { builder, annotation, session ->
        if (annotation is FirAnnotationCall) {
            builder.entity(annotation, ANNOTATION_CALL, session)
        }
    }

    private val ANNOTATION_CALL: StateKeeper<FirAnnotationCall, FirSession> = stateKeeper { builder, annotationCall, session ->
        builder.add(FirAnnotationCall::calleeReference, FirAnnotationCall::replaceCalleeReference)

        val argumentList = annotationCall.argumentList
        if (argumentList !is FirResolvedArgumentList && argumentList !is FirEmptyArgumentList) {
            builder.add(FirAnnotationCall::argumentList, FirAnnotationCall::replaceArgumentList) { oldList ->
                val newArguments = FirLazyBodiesCalculator.createArgumentsForAnnotation(annotationCall, session).arguments
                buildArgumentList {
                    source = oldList.source
                    for ((index, argument) in oldList.arguments.withIndex()) {
                        val replacement = when {
                            argument is FirPropertyAccessExpression && argument.calleeReference.let { it.isError() || it is FirResolvedNamedReference } -> argument
                            else -> newArguments[index]
                        }

                        arguments.add(replacement)
                    }
                }
            }
        }
    }

    val DECLARATION: StateKeeper<FirElementWithResolveState, FirSession> = stateKeeper { builder, target, session ->
        val visitor = object : NonLocalAnnotationVisitor<Unit>() {
            override fun processAnnotation(annotation: FirAnnotation, data: Unit) {
                builder.entity(annotation, ANNOTATION, session)
            }
        }

        target.accept(visitor, Unit)
    }
}

private val FirAnnotation.typeArgumentsCopied: List<FirTypeProjection>
    get() = if (typeArguments.isEmpty()) emptyList() else ArrayList(typeArguments)
