// Copyright (c) 2011, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.google.dart.compiler.backend.js.ast;

public abstract class JsLiteral extends JsExpression implements CanBooleanEval {
    public static final JsThisRef THIS = new JsThisRef();
    public static final JsNameRef UNDEFINED = new JsNameRef("undefined");

    public static final JsNullLiteral NULL = new JsNullLiteral();

    public static final JsBooleanLiteral TRUE = new JsBooleanLiteral(true);
    public static final JsBooleanLiteral FALSE = new JsBooleanLiteral(false);

    public static JsBooleanLiteral getBoolean(boolean truth) {
        return truth ? TRUE : FALSE;
    }
}
