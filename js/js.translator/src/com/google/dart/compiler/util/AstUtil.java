// Copyright (c) 2011, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.google.dart.compiler.util;

import com.google.dart.compiler.backend.js.ast.*;
import com.google.dart.compiler.common.SourceInfo;

/**
 * @author johnlenz@google.com (John Lenz)
 */
public final class AstUtil {
    public static JsNameRef newQualifiedNameRef(String name) {
        JsNameRef node = null;
        int endPos;
        int startPos = 0;
        do {
            endPos = name.indexOf('.', startPos);
            String part = (endPos == -1
                           ? name.substring(startPos)
                           : name.substring(startPos, endPos));
            node = new JsNameRef(part, node);
            startPos = endPos + 1;
        }
        while (endPos != -1);

        return node;
    }

    public static JsArrayAccess newArrayAccess(JsExpression target, JsExpression key) {
        JsArrayAccess arr = new JsArrayAccess();
        arr.setArrayExpr(target);
        arr.setIndexExpr(key);
        return arr;
    }

    /**
     * Returns a sequence of expressions (using the binary sequence operator).
     *
     * @param exprs - expressions to add to sequence
     * @return a sequence of expressions.
     */
    public static JsBinaryOperation newSequence(JsExpression... exprs) {
        if (exprs.length < 2) {
            throw new RuntimeException("newSequence expects at least two arguments");
        }
        JsExpression result = exprs[exprs.length - 1];
        for (int i = exprs.length - 2; i >= 0; i--) {
            result = new JsBinaryOperation(JsBinaryOperator.COMMA, exprs[i], result);
        }
        return (JsBinaryOperation) result;
    }

    public static JsBinaryOperation comma(SourceInfo src, JsExpression op1, JsExpression op2) {
        return (JsBinaryOperation) new JsBinaryOperation(JsBinaryOperator.COMMA, op1, op2)
                .setSourceRef(src);
    }

    public static JsExpression not(SourceInfo src, JsExpression op1) {
        return new JsPrefixOperation(JsUnaryOperator.NOT, op1).setSourceRef(src);
    }

    public static JsExpression and(SourceInfo src, JsExpression op1, JsExpression op2) {
        return new JsBinaryOperation(JsBinaryOperator.AND, op1, op2).setSourceRef(src);
    }

    public static JsExpression in(SourceInfo src, JsExpression propName, JsExpression obj) {
        return new JsBinaryOperation(JsBinaryOperator.INOP, propName, obj).setSourceRef(src);
    }
}
