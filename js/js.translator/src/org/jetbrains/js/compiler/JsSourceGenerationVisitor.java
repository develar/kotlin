/*
 * Copyright 2010-2013 JetBrains s.r.o.
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

package org.jetbrains.js.compiler;

import com.google.dart.compiler.backend.js.JsToStringGenerationVisitor;
import com.google.dart.compiler.backend.js.ast.JsBlock;
import com.google.dart.compiler.backend.js.ast.JsNameRef;
import com.google.dart.compiler.backend.js.ast.JsNode;
import com.google.dart.compiler.util.TextOutput;
import org.jetbrains.annotations.Nullable;

public class JsSourceGenerationVisitor extends JsToStringGenerationVisitor implements TextOutput.OutListener {
    @Nullable
    private final SourceMapBuilder sourceMapBuilder;

    private Object pendingSourceInfo;

    public JsSourceGenerationVisitor(TextOutput out, @Nullable SourceMapBuilder sourceMapBuilder) {
        super(out);
        this.sourceMapBuilder = sourceMapBuilder;
        out.setOutListener(this);
    }

    @Override
    public void visitBlock(JsBlock block) {
        printBlock(block, false);
        if (block.isGlobalBlock() && sourceMapBuilder != null) {
            sourceMapBuilder.addLink();
        }
    }

    @Override
    public void newLined() {
        if (sourceMapBuilder != null) {
            sourceMapBuilder.newLine();
        }
    }

    @Override
    public void indentedAfterNewLine() {
        if (pendingSourceInfo != null) {
            assert sourceMapBuilder != null;
            sourceMapBuilder.processSourceInfo(pendingSourceInfo);
            pendingSourceInfo = null;
        }
    }

    @Override
    public void accept(JsNode node) {
        if (!(node instanceof JsNameRef)) {
            mapSource(node);
        }
        super.accept(node);
    }

    private void mapSource(JsNode node) {
        if (sourceMapBuilder != null) {
            Object sourceInfo = node.getSource();
            if (sourceInfo != null) {
                //assert pendingSourceInfo == null;
                if (p.isJustNewlined()) {
                    //System.out.println(node.toString());
                    pendingSourceInfo = sourceInfo;
                }
                else {
                    sourceMapBuilder.processSourceInfo(sourceInfo);
                }
            }
        }
    }

    @Override
    protected void beforeNodePrinted(JsNode node) {
        mapSource(node);
    }
}