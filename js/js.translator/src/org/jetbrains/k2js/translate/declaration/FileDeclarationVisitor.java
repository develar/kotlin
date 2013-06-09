package org.jetbrains.k2js.translate.declaration;

import com.google.dart.compiler.backend.js.ast.*;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.PropertyDescriptor;
import org.jetbrains.jet.lang.psi.JetClassInitializer;
import org.jetbrains.jet.lang.psi.JetExpression;
import org.jetbrains.jet.lang.psi.JetObjectDeclaration;
import org.jetbrains.jet.lang.psi.JetProperty;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.expression.GenerationPlace;
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

    private final List<JsStatement> initializerStatements;
    // property define statements must be after function define statements
    private List<JsStatement> propertyDefineStatements;

    private final InitializerVisitor initializerVisitor;

    FileDeclarationVisitor(TranslationContext context) {
        super(context);

        initializer = JsAstUtils.createFunctionWithEmptyBody(context.scope());
        initializerContext = context.contextWithScope(initializer);
        initializerStatements = initializer.getBody().getStatements();
        initializerVisitor = new InitializerVisitor(initializerStatements, initializerContext);
    }

    public GenerationPlace createGenerationPlace() {
        return new ClosureBackedGenerationPlace(initializerStatements);
    }

    public boolean finalizeInitializerStatements() {
        if (propertyDefineStatements != null) {
            initializerStatements.addAll(propertyDefineStatements);
        }
        return initializerStatements.isEmpty();
    }

    @Override
    protected void defineFunction(String name, JsExpression value) {
        JsExpression defineExpression;
        if (context.isEcma5()) {
            defineExpression = JsAstUtils.defineProperty(name, value);
        }
        else {
            defineExpression = JsAstUtils.assignment(new JsNameRef(name, JsLiteral.THIS), value);
        }
        initializerStatements.add(defineExpression.asStatement());
    }

    @Override
    public void visitObjectDeclaration(@NotNull JetObjectDeclaration declaration) {
        InitializerUtils.generate(declaration, initializerStatements, context);
    }

    @Override
    public void visitProperty(@NotNull JetProperty property) {
        super.visitProperty(property);

        JetExpression initializer = property.getInitializer();
        if (initializer == null) {
            return;
        }

        if (propertyDefineStatements == null) {
            propertyDefineStatements = new SmartList<JsStatement>();
        }

        JsExpression value = Translation.translateAsExpression(initializer, initializerContext);
        PropertyDescriptor propertyDescriptor = getPropertyDescriptor(context.bindingContext(), property);
        if (value instanceof JsLiteral) {
            JsNameRef name = context.getNameRefForDescriptor(propertyDescriptor);
            JsExpression defineExpression;
            if (context.isEcma5()) {
                defineExpression = JsAstUtils.defineProperty(name.getName(), JsAstUtils.createDataDescriptor(value, propertyDescriptor.isVar(), false));
            }
            else {
                defineExpression = JsAstUtils.assignment(new JsNameRef(name.getName(), JsLiteral.THIS), value);
            }
            propertyDefineStatements.add(defineExpression.asStatement());
        }
        else {
            propertyDefineStatements.add(generateInitializerForProperty(context, propertyDescriptor, value));
        }
    }

    @Override
    public void visitAnonymousInitializer(@NotNull JetClassInitializer expression) {
        expression.accept(initializerVisitor);
    }
}