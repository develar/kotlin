package org.jetbrains.k2js.translate.declaration;

import com.google.dart.compiler.backend.js.ast.JsFunction;
import com.google.dart.compiler.backend.js.ast.JsNameRef;
import com.google.dart.compiler.backend.js.ast.JsNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.MemberDescriptor;
import org.jetbrains.k2js.translate.context.TranslationContext;
import org.jetbrains.k2js.translate.expression.GenerationPlace;

import java.util.List;

public class ClosureBackedGenerationPlace implements GenerationPlace {
    private int nameCounter;
    private final List<JsNode> statements;

    public ClosureBackedGenerationPlace(List<JsNode> statements) {
        this.statements = statements;
    }

    @Override
    public JsNameRef createReference(
            @NotNull JsFunction fun, @NotNull MemberDescriptor descriptor, @NotNull TranslationContext context
    ) {
        String name = "$f" + Integer.toString(nameCounter++, 36);
        JsNameRef nameRef = new JsNameRef(name);
        assert fun.getName() == null;
        fun.setName(name);
        statements.add(fun);
        return nameRef;
    }
}