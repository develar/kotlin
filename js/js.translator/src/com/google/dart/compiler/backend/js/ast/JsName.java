// Copyright (c) 2011, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.google.dart.compiler.backend.js.ast;

import com.google.dart.compiler.common.Symbol;

import java.io.Serializable;

/**
 * An abstract base class for named JavaScript objects.
 */
public class JsName implements Symbol, Serializable {
  private final JsScope enclosing;
  private final String ident;
  private String originalName;

  /**
   * A back-reference to the JsNode that the JsName refers to.
   */
  private JsNode staticRef;

  /**
   * @param ident the unmangled ident to use for this name
   */
  JsName(JsScope enclosing, String ident, String originalName) {
    this.enclosing = enclosing;
    this.ident = ident;
    if (originalName != null) {
      this.originalName = originalName;
    }
  }

  public JsScope getEnclosing() {
    return enclosing;
  }

  public String getIdent() {
    return ident;
  }

  public String getOriginalName() {
    return originalName;
  }

  public JsNameRef makeRef() {
    return new JsNameRef(this);
  }

  public void setObfuscatable(boolean isObfuscatable) {
  }

  /**
   * Should never be called except on immutable stuff.
   */
  public void setStaticRef(JsNode node) {
    this.staticRef = node;
  }

  @Override
  public String toString() {
    return ident;
  }

  @Override
  public String getOriginalSymbolName() {
    return getOriginalName();
  }

  @Override
  public int hashCode() {
    return ident.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof JsName)) {
      return false;
    }
    JsName other = (JsName) obj;
    return ident.equals(other.ident) && enclosing == other.enclosing;
  }
}
