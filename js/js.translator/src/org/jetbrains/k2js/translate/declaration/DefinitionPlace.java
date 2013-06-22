package org.jetbrains.k2js.translate.declaration;

import com.google.dart.compiler.backend.js.ast.JsFunction;
import com.google.dart.compiler.backend.js.ast.JsNameRef;
import com.google.dart.compiler.backend.js.ast.JsNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.MemberDescriptor;
import org.jetbrains.k2js.translate.context.TranslationContext;

import java.util.List;

public class DefinitionPlace {
    private int nameCounter;
    private final List<JsNode> nodes;

    public DefinitionPlace(List<JsNode> nodes) {
        this.nodes = nodes;
    }

    public List<JsNode> getNodes() {
        return nodes;
    }

    public JsNameRef createReference(
            @NotNull JsFunction fun, @NotNull MemberDescriptor descriptor, @NotNull TranslationContext context
    ) {
        String name = "$f" + Integer.toString(nameCounter++, 36);
        JsNameRef nameRef = new JsNameRef(name);
        assert fun.getName() == null;
        fun.setName(name);
        nodes.add(fun);
        return nameRef;
    }
}