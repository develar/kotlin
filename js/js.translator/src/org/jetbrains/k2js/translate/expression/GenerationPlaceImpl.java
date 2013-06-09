package org.jetbrains.k2js.translate.expression;

import com.google.dart.compiler.backend.js.ast.JsExpression;
import com.google.dart.compiler.backend.js.ast.JsFunction;
import com.google.dart.compiler.backend.js.ast.JsNameRef;
import com.google.dart.compiler.backend.js.ast.JsPropertyInitializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.MemberDescriptor;
import org.jetbrains.k2js.translate.LabelGenerator;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.initializer.InitializerUtils;

import java.util.List;

public final class GenerationPlaceImpl implements GenerationPlace {
    private final List<JsPropertyInitializer> propertyInitializers;
    private final LabelGenerator labelGenerator;
    private final JsExpression expression;

    public GenerationPlaceImpl(
            List<JsPropertyInitializer> propertyInitializers,
            LabelGenerator labelGenerator,
            JsExpression expression
    ) {
        this.propertyInitializers = propertyInitializers;
        this.labelGenerator = labelGenerator;
        this.expression = expression;
    }

    @Override
    public JsNameRef createReference(@NotNull JsFunction fun, @NotNull MemberDescriptor descriptor, @NotNull TranslationContext context) {
        JsNameRef nameRef = new JsNameRef(labelGenerator.generate(null), expression);
        propertyInitializers.add(new JsPropertyInitializer(nameRef, InitializerUtils.toDataDescriptor(fun, context)));
        return nameRef;
    }
}