/*
 * Copyright 2010-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.jet.plugin.highlighter;

import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.SyntaxHighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.TextAttributesKeyDefaults;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;

import java.awt.*;

import static com.intellij.openapi.editor.colors.TextAttributesKeyDefaults.*;
import static com.intellij.openapi.editor.colors.TextAttributesKeyDefaults.getDefaultAttributes;

public class JetHighlightingColors {
    public final static TextAttributesKey KEYWORD = createTextAttributesKey(
            "KOTLIN_KEYWORD", getDefaultAttributes(SyntaxHighlighterColors.KEYWORD));

    public static final TextAttributesKey BUILTIN_ANNOTATION = createTextAttributesKey(
            "KOTLIN_BUILTIN_ANNOTATION", getDefaultAttributes(SyntaxHighlighterColors.KEYWORD)
    );

    public static final TextAttributesKey NUMBER = createTextAttributesKey(
            "KOTLIN_NUMBER", getDefaultAttributes(SyntaxHighlighterColors.NUMBER)
    );

    public static final TextAttributesKey STRING = createTextAttributesKey(
            "KOTLIN_STRING", getDefaultAttributes(SyntaxHighlighterColors.STRING)
    );

    public static final TextAttributesKey STRING_ESCAPE = createTextAttributesKey(
            "KOTLIN_STRING_ESCAPE", getDefaultAttributes(SyntaxHighlighterColors.VALID_STRING_ESCAPE)
    );

    public static final TextAttributesKey INVALID_STRING_ESCAPE = createTextAttributesKey(
            "KOTLIN_INVALID_STRING_ESCAPE",
            new TextAttributes(null, getDefaultAttributes(HighlighterColors.BAD_CHARACTER).getBackgroundColor(),
                               Color.RED, EffectType.WAVE_UNDERSCORE, 0)
    );

    public static final TextAttributesKey OPERATOR_SIGN = createTextAttributesKey(
            "KOTLIN_OPERATION_SIGN", getDefaultAttributes(SyntaxHighlighterColors.OPERATION_SIGN)
    );

    public static final TextAttributesKey PARENTHESIS = createTextAttributesKey(
            "KOTLIN_PARENTHESIS", getDefaultAttributes(SyntaxHighlighterColors.PARENTHS)
    );

    public static final TextAttributesKey BRACES = createTextAttributesKey(
            "KOTLIN_BRACES", getDefaultAttributes(SyntaxHighlighterColors.BRACES)
    );

    public static final TextAttributesKey BRACKETS = createTextAttributesKey(
            "KOTLIN_BRACKETS", getDefaultAttributes(SyntaxHighlighterColors.BRACKETS)
    );

    public static final TextAttributesKey FUNCTION_LITERAL_BRACES_AND_ARROW = createTextAttributesKey(
            "KOTLIN_FUNCTION_LITERAL_BRACES_AND_ARROW",
            new TextAttributes(null, null, null, null, Font.BOLD)
    );

    public static final TextAttributesKey COMMA = createTextAttributesKey(
            "KOTLIN_COMMA", getDefaultAttributes(SyntaxHighlighterColors.COMMA)
    );

    public static final TextAttributesKey SEMICOLON = createTextAttributesKey(
            "KOTLIN_SEMICOLON", getDefaultAttributes(SyntaxHighlighterColors.JAVA_SEMICOLON)
    );

    public static final TextAttributesKey DOT = createTextAttributesKey(
            "KOTLIN_DOT", getDefaultAttributes(SyntaxHighlighterColors.DOT)
    );

    public static final TextAttributesKey SAFE_ACCESS = createTextAttributesKey(
            "KOTLIN_SAFE_ACCESS", getDefaultAttributes(SyntaxHighlighterColors.DOT)
    );

    public static final TextAttributesKey ARROW = createTextAttributesKey(
            "KOTLIN_ARROW", getDefaultAttributes(SyntaxHighlighterColors.PARENTHS)
    );

    public static final TextAttributesKey LINE_COMMENT = createTextAttributesKey(
            "KOTLIN_LINE_COMMENT", getDefaultAttributes(SyntaxHighlighterColors.LINE_COMMENT)
    );

    public static final TextAttributesKey BLOCK_COMMENT = createTextAttributesKey(
            "KOTLIN_BLOCK_COMMENT",
            getDefaultAttributes(SyntaxHighlighterColors.JAVA_BLOCK_COMMENT)
    );

    public static final TextAttributesKey DOC_COMMENT = createTextAttributesKey(
            "KOTLIN_DOC_COMMENT",
            getDefaultAttributes(SyntaxHighlighterColors.DOC_COMMENT)
    );

    public static final TextAttributesKey DOC_COMMENT_TAG = createTextAttributesKey(
            "KOTLIN_DOC_COMMENT_TAG",
            getDefaultAttributes(SyntaxHighlighterColors.DOC_COMMENT_TAG)
    );

    public static final TextAttributesKey DOC_COMMENT_TAG_VALUE = createTextAttributesKey(
            "KOTLIN_DOC_COMMENT_TAG_VALUE",
            getDefaultAttributes(CodeInsightColors.DOC_COMMENT_TAG_VALUE)
    );

    public static final TextAttributesKey DOC_COMMENT_MARKUP = createTextAttributesKey(
            "KOTLIN_DOC_COMMENT_MARKUP",
            getDefaultAttributes(SyntaxHighlighterColors.DOC_COMMENT_MARKUP)
    );

    public static final TextAttributesKey CLASS = createTextAttributesKey(
            "KOTLIN_CLASS",
            getDefaultAttributes(CodeInsightColors.CLASS_NAME_ATTRIBUTES)
    );

    public static final TextAttributesKey TYPE_PARAMETER = createTextAttributesKey(
            "KOTLIN_TYPE_PARAMETER",
            getDefaultAttributes(CodeInsightColors.TYPE_PARAMETER_NAME_ATTRIBUTES)
    );

    public static final TextAttributesKey ABSTRACT_CLASS = createTextAttributesKey(
            "KOTLIN_ABSTRACT_CLASS",
            getDefaultAttributes(CodeInsightColors.ABSTRACT_CLASS_NAME_ATTRIBUTES)
    );

    public static final TextAttributesKey TRAIT = createTextAttributesKey(
            "KOTLIN_TRAIT",
            getDefaultAttributes(CodeInsightColors.INTERFACE_NAME_ATTRIBUTES)
    );

    public static final TextAttributesKey ANNOTATION = createTextAttributesKey(
            "KOTLIN_ANNOTATION",
            getDefaultAttributes(CodeInsightColors.ANNOTATION_NAME_ATTRIBUTES)
    );

    public static final TextAttributesKey MUTABLE_VARIABLE = TextAttributesKeyDefaults.createTextAttributesKey(
            "KOTLIN_MUTABLE_VARIABLE",
            new TextAttributes(null, null, Color.BLACK, EffectType.LINE_UNDERSCORE, 0)
    );

    public static final TextAttributesKey LOCAL_VARIABLE = createTextAttributesKey(
            "KOTLIN_LOCAL_VARIABLE",
            getDefaultAttributes(CodeInsightColors.LOCAL_VARIABLE_ATTRIBUTES)
    );

    public static final TextAttributesKey PARAMETER = createTextAttributesKey(
            "KOTLIN_PARAMETER",
            getDefaultAttributes(CodeInsightColors.PARAMETER_ATTRIBUTES)
    );

    public static final TextAttributesKey WRAPPED_INTO_REF = createTextAttributesKey(
            "KOTLIN_WRAPPED_INTO_REF",
            getDefaultAttributes(CodeInsightColors.IMPLICIT_ANONYMOUS_CLASS_PARAMETER_ATTRIBUTES)
    );

    public static final TextAttributesKey INSTANCE_PROPERTY = createTextAttributesKey(
            "KOTLIN_INSTANCE_PROPERTY",
            getDefaultAttributes(CodeInsightColors.INSTANCE_FIELD_ATTRIBUTES)
    );

    public static final TextAttributesKey NAMESPACE_PROPERTY = createTextAttributesKey(
            "KOTLIN_NAMESPACE_PROPERTY",
            getDefaultAttributes(CodeInsightColors.STATIC_FIELD_ATTRIBUTES)
    );

    public static final TextAttributesKey PROPERTY_WITH_BACKING_FIELD = createTextAttributesKey(
            "KOTLIN_PROPERTY_WITH_BACKING_FIELD",
            new TextAttributes(null, new Color(0xf5d7ef), null, null, 0)
    );

    public static final TextAttributesKey BACKING_FIELD_ACCESS = createTextAttributesKey(
            "KOTLIN_BACKING_FIELD_ACCESS",
            new TextAttributes()
    );

    public static final TextAttributesKey EXTENSION_PROPERTY = createTextAttributesKey(
            "KOTLIN_EXTENSION_PROPERTY",
            new TextAttributes()
    );

    public static final TextAttributesKey FUNCTION_LITERAL_DEFAULT_PARAMETER = createTextAttributesKey(
            "KOTLIN_CLOSURE_DEFAULT_PARAMETER",
            new TextAttributes(null, null, null, null, Font.BOLD)
    );

    public static final TextAttributesKey FUNCTION_DECLARATION = createTextAttributesKey(
            "KOTLIN_FUNCTION_DECLARATION",
            getDefaultAttributes(CodeInsightColors.METHOD_DECLARATION_ATTRIBUTES)
    );

    public static final TextAttributesKey FUNCTION_CALL = createTextAttributesKey(
            "KOTLIN_FUNCTION_CALL",
            getDefaultAttributes(CodeInsightColors.METHOD_CALL_ATTRIBUTES)
    );

    public static final TextAttributesKey NAMESPACE_FUNCTION_CALL = createTextAttributesKey(
            "KOTLIN_NAMESPACE_FUNCTION_CALL",
            getDefaultAttributes(CodeInsightColors.STATIC_METHOD_ATTRIBUTES)
    );

    public static final TextAttributesKey EXTENSION_FUNCTION_CALL = createTextAttributesKey(
            "KOTLIN_EXTENSION_FUNCTION_CALL",
            new TextAttributes()
    );

    public static final TextAttributesKey CONSTRUCTOR_CALL = createTextAttributesKey(
            "KOTLIN_CONSTRUCTOR",
            getDefaultAttributes(CodeInsightColors.CONSTRUCTOR_CALL_ATTRIBUTES)
    );

    public static final TextAttributesKey BAD_CHARACTER = createTextAttributesKey(
            "KOTLIN_BAD_CHARACTER",
            getDefaultAttributes(HighlighterColors.BAD_CHARACTER)
    );

    public static final TextAttributesKey AUTO_CASTED_VALUE = createTextAttributesKey(
            "KOTLIN_AUTO_CASTED_VALUE",
            new TextAttributes(null, new Color(0xdbffdb), null, null, Font.PLAIN)
    );

    public static final TextAttributesKey LABEL = createTextAttributesKey(
            "KOTLIN_LABEL",
            new TextAttributes(new Color(0x4a86e8), null, null, null, Font.PLAIN)
    );

    public static final TextAttributesKey DEBUG_INFO = createTextAttributesKey(
            "KOTLIN_DEBUG_INFO",
            new TextAttributes(null, null, Color.BLACK, EffectType.ROUNDED_BOX, Font.PLAIN)
    );

    public static final TextAttributesKey RESOLVED_TO_ERROR = createTextAttributesKey(
            "KOTLIN_RESOLVED_TO_ERROR",
            new TextAttributes(null, null, Color.RED, EffectType.ROUNDED_BOX, Font.PLAIN)
    );

    private JetHighlightingColors() {
    }
}
