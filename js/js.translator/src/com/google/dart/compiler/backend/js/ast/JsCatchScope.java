// Copyright (c) 2011, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.google.dart.compiler.backend.js.ast;

import org.jetbrains.annotations.NotNull;

/**
 * A special scope used only for catch blocks. It only holds a single symbol:
 * the catch argument's name.
 */
public class JsCatchScope extends JsScope {
    private final JsName name;

    public JsCatchScope(JsScope parent, String ident) {
        super(parent, "Catch scope");
        this.name = new JsName(this, ident, ident);
    }

    @Override
    public JsName declareName(String identifier) {
        // Declare into parent scope!
        return getParent().declareName(identifier);
    }

    @Override
    public boolean hasOwnName(@NotNull String name) {
        return this.name.getIdent().equals(name);
    }

    @Override
    protected JsName doCreateName(String ident, String originalName) {
        throw new UnsupportedOperationException("Cannot create a name in a catch scope");
    }

    @Override
    protected JsName findExistingNameNoRecurse(String ident) {
        return name.getIdent().equals(ident) ? name : null;
    }
}