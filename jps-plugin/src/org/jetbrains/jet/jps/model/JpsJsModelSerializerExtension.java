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

package org.jetbrains.jet.jps.model;

import org.jdom.Element;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.artifact.JpsPackagingElementSerializer;

import java.util.Collections;
import java.util.List;

public class JpsJsModelSerializerExtension extends JpsModelSerializerExtension {
    @Override
    public List<? extends JpsPackagingElementSerializer<?>> getPackagingElementSerializers() {
        return Collections.singletonList(new JpsJsCompilerOutputElementSerializer(JsExternalizationConstants.COMPILER_OUTPUT_ELEMENT_ID));
    }

    private static class JpsJsCompilerOutputElementSerializer extends JpsPackagingElementSerializer<JpsKotlinCompilerOutputPackagingElement> {
        public JpsJsCompilerOutputElementSerializer(String typeId) {
            super(typeId, JpsKotlinCompilerOutputPackagingElement.class);
        }

        @Override
        public JpsKotlinCompilerOutputPackagingElement load(Element element) {
            JpsModuleReference moduleReference = JpsElementFactory.getInstance().createModuleReference(element.getAttributeValue("name"));
            return new JpsKotlinCompilerOutputPackagingElement(moduleReference);
        }

        @Override
        public void save(JpsKotlinCompilerOutputPackagingElement element, Element tag) {
            tag.setAttribute("name", element.getModuleReference().getModuleName());
        }
    }
}
