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

package org.jetbrains.k2js.translate;

import org.jetbrains.annotations.Nullable;

public final class LabelGenerator {
    private int nameCounter;
    private final char prefix;

    public LabelGenerator(char prefix) {
        this.prefix = prefix;
    }

    public String generate(@Nullable String ns) {
        StringBuilder builder = new StringBuilder();
        if (ns != null) {
            builder.append(ns).append('$');
        }
        return builder.append(prefix).append(Integer.toString(nameCounter++, 36)).toString();
    }
}
