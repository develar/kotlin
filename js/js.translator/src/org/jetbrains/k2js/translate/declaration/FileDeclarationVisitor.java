package org.jetbrains.k2js.translate.declaration;

import com.google.dart.compiler.backend.js.ast.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.PropertyDescriptor;
import org.jetbrains.jet.lang.psi.JetClassInitializer;
import org.jetbrains.jet.lang.psi.JetExpression;
import org.jetbrains.jet.lang.psi.JetObjectDeclaration;
import org.jetbrains.jet.lang.psi.JetProperty;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.general.Translation;
import org.jetbrains.k2js.translate.initializer.InitializerUtils;
import org.jetbrains.k2js.translate.initializer.InitializerVisitor;
import org.jetbrains.k2js.translate.utils.JsAstUtils;

import java.util.List;

import static org.jetbrains.k2js.translate.initializer.InitializerUtils.generateInitializerForProperty;
import static org.jetbrains.k2js.translate.utils.BindingUtils.getPropertyDescriptor;

final class FileDeclarationVisitor extends DeclarationBodyVisitor {
    final JsFunction initializer;
    private final TranslationContext initializerContext;
    final List<JsStatement> initializerStatements;
    private final InitializerVisitor initializerVisitor;

    FileDeclarationVisitor(TranslationContext context) {
        super(context);

        initializer = JsAstUtils.createFunctionWithEmptyBody(context.scope());
        initializerContext = context.contextWithScope(initializer);
        initializerStatements = initializer.getBody().getStatements();
        initializerVisitor = new InitializerVisitor(initializerStatements, initializerContext);
    }

    @Override
    public void visitObjectDeclaration(@NotNull JetObjectDeclaration declaration) {
        InitializerUtils.generate(declaration, initializerStatements, context);
    }

    @Override
    public void visitProperty(@NotNull JetProperty property) {
        super.visitProperty(property);

        JetExpression initializer = property.getInitializer();
        if (initializer != null) {
            JsExpression value = Translation.translateAsExpression(initializer, initializerContext);
            PropertyDescriptor propertyDescriptor = getPropertyDescriptor(context.bindingContext(), property);
            if (value instanceof JsLiteral) {
                result.add(new JsPropertyInitializer(context.getNameRefForDescriptor(propertyDescriptor),
                                                     context.isEcma5() ? JsAstUtils
                                                             .createPropertyDataDescriptor(propertyDescriptor, value, context) : value));
            }
            else {
                initializerStatements.add(generateInitializerForProperty(context, propertyDescriptor, value));
            }
        }
    }

    @Override
    public void visitAnonymousInitializer(@NotNull JetClassInitializer expression) {
        expression.accept(initializerVisitor);
    }
}
