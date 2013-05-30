package org.jetbrains.k2js.translate.expression;

import com.google.dart.compiler.backend.js.ast.JsExpression;
import com.google.dart.compiler.backend.js.ast.JsPropertyInitializer;
import org.jetbrains.k2js.translate.LabelGenerator;

import java.util.List;

public class GenerationPlace {
    private final List<JsPropertyInitializer> propertyInitializers;
    private final LabelGenerator labelGenerator;
    private final JsExpression expression;

    public GenerationPlace(
            List<JsPropertyInitializer> propertyInitializers,
            LabelGenerator labelGenerator,
            JsExpression expression
    ) {
        this.propertyInitializers = propertyInitializers;
        this.labelGenerator = labelGenerator;
        this.expression = expression;
    }

    public List<JsPropertyInitializer> getPropertyInitializers() {
        return propertyInitializers;
    }

    public LabelGenerator getLabelGenerator() {
        return labelGenerator;
    }

    public JsExpression getExpression() {
        return expression;
    }
}