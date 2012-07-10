// Copyright (c) 2011, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.google.dart.compiler.backend.js.ast;

import com.google.dart.compiler.util.Lists;
import com.google.dart.compiler.util.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A scope is a factory for creating and allocating
 * {@link JsName}s. A JavaScript AST is
 * built in terms of abstract name objects without worrying about obfuscation,
 * keyword/identifier blacklisting, and so on.
 * <p/>
 * <p/>
 * <p/>
 * Scopes are associated with
 * {@link JsFunction}s, but the two are
 * not equivalent. Functions <i>have</i> scopes, but a scope does not
 * necessarily have an associated Function. Examples of this include the
 * {@link JsRootScope} and synthetic
 * scopes that might be created by a client.
 * <p/>
 * <p/>
 * <p/>
 * Scopes can have parents to provide constraints when allocating actual
 * identifiers for names. Specifically, names in child scopes are chosen such
 * that they do not conflict with names in their parent scopes. The ultimate
 * parent is usually the global scope (see
 * {@link JsProgram#getRootScope()}),
 * but parentless scopes are useful for managing names that are always accessed
 * with a qualifier and could therefore never be confused with the global scope
 * hierarchy.
 */
public class JsScope implements Serializable {
    private List<JsScope> children = Collections.emptyList();
    @Nullable
    private final String description;
    private Map<String, JsName> names = Collections.emptyMap();
    private JsScope parent;
    protected int tempIndex = 0;
    private final String scopeId;

    public JsScope(JsScope parent, @Nullable String description) {
        this(parent, description, null);
    }

    public JsScope(JsScope parent) {
        this(parent, null);
    }

    public JsScope(JsScope parent, @Nullable String description, @Nullable String scopeId) {
        assert (parent != null);
        this.scopeId = scopeId;
        this.description = description;
        this.parent = parent;
        parent.children = Lists.add(parent.children, this);
    }

    @NotNull
    public JsScope innerScope(@Nullable String scopeName) {
        return new JsScope(this, scopeName);
    }

    /**
     * Rebase the function to a new scope.
     *
     * @param newParent The scope to add the function to.
     */
    public void rebase(JsScope newParent) {
        detachFromParent();
        parent = newParent;
        parent.children = Lists.add(parent.children, this);
    }

    /**
     * Rebase the function's children to a new scope.
     *
     * @param newParent
     */
    public void rebaseChildScopes(JsScope newParent) {
        if (newParent == this) {
            return;
        }
        parent.children = Lists.addAll(parent.children, children);
        for (JsScope child : children) {
            child.parent = newParent;
        }
        children = Collections.emptyList();
    }

    /**
     * Subclasses can detach and become parentless.
     */
    protected void detachFromParent() {
        JsScope oldParent = parent;

        oldParent.children = Lists.remove(
                parent.children, oldParent.children.indexOf(this));

        parent = null;
    }

    /**
     * Subclasses can be parentless.
     */
    protected JsScope(@Nullable String description) {
        this.description = description;
        this.parent = null;
        this.scopeId = null;
    }

    /**
     * Gets a name object associated with the specified identifier in this scope,
     * creating it if necessary.<br/>
     * If the JsName does not exist yet, a new JsName is created. The identifier,
     * short name, and original name of the newly created JsName are equal to
     * the given identifier.
     *
     * @param identifier An identifier that is unique within this scope.
     */
    public JsName declareName(String identifier) {
        JsName name = findExistingNameNoRecurse(identifier);
        return name != null ? name : doCreateName(identifier, identifier);
    }

    /**
     * Creates a new variable with an unique ident in this scope.
     * The generated JsName is guaranteed to have an identifier (but not short
     * name) that does not clash with any existing variables in the scope.
     * Future declarations of variables might however clash with the temporary
     * (unless they use this function).
     */
    public JsName declareFreshName(String shortName) {
        String ident = shortName;
        int counter = 0;
        while (findExistingNameNoRecurse(ident) != null) {
            ident = shortName + "_" + counter++;
        }
        return doCreateName(ident, shortName);
    }

    String getNextTempName() {
        // introduced by the compiler
        return "tmp$" + (scopeId != null ? scopeId + "$" : "") + tempIndex++;
    }

    /**
     * Creates a temporary variable with an unique name in this scope.
     * The generated temporary is guaranteed to have an identifier (but not short
     * name) that does not clash with any existing variables in the scope.
     * Future declarations of variables might however clash with the temporary.
     */
    public JsName declareTemporary() {
        return declareFreshName(getNextTempName());
    }

    /**
     * Gets a name object associated with the specified ident in this scope,
     * creating it if necessary.<br/>
     * If the JsName does not exist yet, a new JsName is created. The original
     * name stored in the JsName is equal to the (unmangled) specified originalName.
     *
     * @param ident        An identifier that is unique within this scope.
     * @param originalName The original name in the source.
     * @throws IllegalArgumentException if ident already exists in this scope but
     *                                  the requested short name does not match the existing short name,
     *                                  or the original name does not match the existing original name.
     */
    public JsName declareName(String ident, String originalName) {
        JsName name = findExistingNameNoRecurse(ident);
        return name != null ? name : doCreateName(ident, originalName);
    }

    /**
     * Attempts to find the name object for the specified ident, searching in this
     * scope, and if not found, in the parent scopes.
     *
     * @return <code>null</code> if the identifier has no associated name
     */
    public final JsName findExistingName(String ident) {
        JsName name = findExistingNameNoRecurse(ident);
        if (name == null && parent != null) {
            return parent.findExistingName(ident);
        }
        return name;
    }

    protected boolean hasOwnName(@NotNull JsName name) {
        return names.containsValue(name);
    }

    protected boolean hasOwnName(@NotNull String name) {
        return names.get(name) != null;
    }

    /**
     * Returns a list of this scope's child scopes.
     */
    public final List<JsScope> getChildren() {
        return children;
    }

    /**
     * Returns the parent scope of this scope, or <code>null</code> if this is the
     * root scope.
     */
    public final JsScope getParent() {
        return parent;
    }

    /**
     * Returns the associated program.
     */
    public JsProgram getProgram() {
        assert (parent != null) : "Subclasses must override getProgram() if they do not set a parent";
        return parent.getProgram();
    }

    @Override
    public final String toString() {
        if (parent != null) {
            return description + "->" + parent;
        }
        else {
            return description;
        }
    }

    /**
     * Creates a new name in this scope.
     */
    protected JsName doCreateName(String ident, String originalName) {
        JsName name = new JsName(this, ident, originalName);
        names = Maps.putOrdered(names, ident, name);
        return name;
    }

    /**
     * Attempts to find the name object for the specified ident, searching in this
     * scope only.
     *
     * @return <code>null</code> if the identifier has no associated name
     */
    protected JsName findExistingNameNoRecurse(String ident) {
        return names.get(ident);
    }

    @NotNull
    public JsName declareUnobfuscatableName(@NotNull String name) {
        return declareName(name);
    }

    @NotNull
    public JsName declareObfuscatableName(@NotNull String name) {
        return declareName(obfuscateName(name));
    }

    @NotNull
    private String obfuscateName(@NotNull String name) {
        int obfuscate = 0;
        String result = name;
        while (hasOwnName(result)) {
            result = name + "$" + obfuscate++;
        }
        return result;
    }
}
