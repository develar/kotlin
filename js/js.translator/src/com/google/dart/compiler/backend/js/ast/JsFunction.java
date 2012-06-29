// Copyright (c) 2011, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.google.dart.compiler.backend.js.ast;

import com.google.dart.compiler.common.SourceInfo;
import com.google.dart.compiler.common.Symbol;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a JavaScript function expression.
 */
public final class JsFunction extends JsLiteral implements HasName {
    private JsBlock body;
    private final List<JsParameter> params = new SmartList<JsParameter>();
    private final JsScope scope;
    private JsName name;

    public JsFunction(JsScope parent) {
        this(parent, null, false);
    }

    public JsFunction(JsScope parent, boolean isDummy) {
        this(parent, null, isDummy);
    }

    public JsFunction(JsScope parent, JsBlock body) {
        this(parent, null, false);
        this.body = body;
    }

    /**
     * Creates a function that is not derived from Dart source.
     */
    public JsFunction(JsScope parent, JsName name) {
        this(parent, name, false);
    }

    private JsFunction(JsScope parent, @Nullable JsName name, boolean isDummy) {
        this(name, new JsScope(parent, "function " + ((name == null) ? "<anonymous>" : name.getIdent())));
    }

    private JsFunction(@Nullable JsName name, JsScope scope) {
        setName(name);
        this.scope = scope;
    }

    public static JsFunction createWithScope(JsScope parent) {
        JsFunction function = new JsFunction(null, parent);
        function.setBody(new JsBlock());
        return function;
    }

    public JsBlock getBody() {
        return body;
    }

    @Override
    public JsName getName() {
        return name;
    }

    @Override
    public Symbol getSymbol() {
        return name;
    }

    public List<JsParameter> getParameters() {
        return params;
    }

    public JsScope getScope() {
        return scope;
    }

    @Override
    public boolean hasSideEffects() {
        // If there's a name, the name is assigned to.
        return name != null;
    }

    @Override
    public boolean isBooleanFalse() {
        return false;
    }

    @Override
    public boolean isBooleanTrue() {
        return true;
    }

    @Override
    public boolean isDefinitelyNotNull() {
        return true;
    }

    @Override
    public boolean isDefinitelyNull() {
        return false;
    }

    public void setBody(JsBlock body) {
        this.body = body;
    }

    public void setName(JsName name) {
        this.name = name;
    }

    @Override
    public void traverse(JsVisitor v, JsContext ctx) {
        if (v.visit(this, ctx)) {
            v.acceptWithInsertRemove(params);
            body = v.accept(body);
        }
        v.endVisit(this, ctx);
    }

    /**
     * Rebase the function to a new scope.
     *
     * @param newScopeParent The scope to add the function to.
     */
    public void rebaseScope(JsScope newScopeParent) {
        this.scope.rebase(newScopeParent);
    }

    @Override
    public JsFunction setSourceRef(SourceInfo info) {
        super.setSourceRef(info);
        return this;
    }

    @Override
    public NodeKind getKind() {
        return NodeKind.FUNCTION;
    }
}
