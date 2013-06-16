package org.jetbrains.k2js.translate.declaration;

import com.google.dart.compiler.backend.js.ast.*;
import com.intellij.util.SmartList;
import org.jetbrains.jet.lang.descriptors.PropertyDescriptor;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.expression.GenerationPlace;
import org.jetbrains.k2js.translate.utils.JsAstUtils;

import java.util.List;

import static org.jetbrains.k2js.translate.initializer.InitializerUtils.generateInitializerForProperty;

final class FileDeclarationVisitor extends DeclarationBodyVisitor {
    // property define statements must be after function define statements
    private List<JsNode> propertyDefineStatements;

    FileDeclarationVisitor(TranslationContext context) {
        super(context);
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
    protected void visitPropertyWithInitializer(PropertyDescriptor descriptor, JsExpression value) {
        if (propertyDefineStatements == null) {
            propertyDefineStatements = new SmartList<JsNode>();
        }

        if (value instanceof JsLiteral) {
            JsNameRef name = context.getNameRefForDescriptor(descriptor);
            JsExpression defineExpression;
            if (context.isEcma5()) {
                defineExpression = JsAstUtils.defineProperty(name.getName(), JsAstUtils.createDataDescriptor(value,
                                                                                                             descriptor.isVar(),
                                                                                                             false));
            }
            else {
                defineExpression = JsAstUtils.assignment(new JsNameRef(name.getName(), JsLiteral.THIS), value);
            }
            propertyDefineStatements.add(defineExpression);
        }
        else {
            propertyDefineStatements.add(generateInitializerForProperty(context, descriptor, value));
        }
    }
}