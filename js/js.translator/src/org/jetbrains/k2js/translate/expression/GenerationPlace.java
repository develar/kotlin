package org.jetbrains.k2js.translate.expression;

import com.google.dart.compiler.backend.js.ast.JsExpression;
import com.google.dart.compiler.backend.js.ast.JsPropertyInitializer;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.MemberDescriptor;
import org.jetbrains.jet.lang.descriptors.SimpleFunctionDescriptor;
import org.jetbrains.jet.lang.resolve.DescriptorUtils;
import org.jetbrains.k2js.translate.LabelGenerator;

import java.util.List;

public final class GenerationPlace {
    private final List<JsPropertyInitializer> propertyInitializers;
    private final LabelGenerator labelGenerator;
    private final JsExpression expression;

    private final boolean generateQualifiedLabel;

    public GenerationPlace(
            List<JsPropertyInitializer> propertyInitializers,
            LabelGenerator labelGenerator,
            JsExpression expression
    ) {
        this(propertyInitializers, labelGenerator, expression, false);
    }

    public GenerationPlace(
            List<JsPropertyInitializer> propertyInitializers,
            LabelGenerator labelGenerator,
            JsExpression expression,
            boolean generateQualifiedLabel
    ) {
        this.propertyInitializers = propertyInitializers;
        this.labelGenerator = labelGenerator;
        this.expression = expression;
        this.generateQualifiedLabel = generateQualifiedLabel;
    }

    public List<JsPropertyInitializer> getPropertyInitializers() {
        return propertyInitializers;
    }

    public String generateLabel(MemberDescriptor descriptor) {
        if (!generateQualifiedLabel) {
            return labelGenerator.generate(null);
        }

        String ns = null;
        DeclarationDescriptor parent = descriptor;
        while ((parent = parent.getContainingDeclaration()) != null) {
            if (parent instanceof SimpleFunctionDescriptor) {
                if (!parent.getName().isSpecial()) {
                    ns = parent.getName().asString();
                    break;
                }
            }
            else if (parent instanceof ClassDescriptor) {
                ClassDescriptor containingClass = DescriptorUtils.getContainingClass(descriptor);
                if (containingClass == null) {
                    break;
                }
                ns = containingClass.getName().asString();
            }
        }
        return labelGenerator.generate(ns);
    }

    public JsExpression getExpression() {
        return expression;
    }
}