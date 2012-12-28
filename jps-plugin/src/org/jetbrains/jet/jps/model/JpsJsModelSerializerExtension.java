package org.jetbrains.jet.jps.model;

import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.artifact.JpsPackagingElementSerializer;
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer;

import java.util.Collections;
import java.util.List;

public class JpsJsModelSerializerExtension extends JpsModelSerializerExtension {
    @NonNls public static final String JS_COMPILER_OUTPUT_ELEMENT_ID = "k2js-compiler-output";

    @Override
    public List<? extends JpsPackagingElementSerializer<?>> getPackagingElementSerializers() {
        return Collections.singletonList(new JpsJsCompilerOutputElementSerializer(JS_COMPILER_OUTPUT_ELEMENT_ID));
    }

    private static class JpsJsCompilerOutputElementSerializer extends JpsPackagingElementSerializer<JsCompilerOutputPackagingElement> {
        public JpsJsCompilerOutputElementSerializer(String typeId) {
            super(typeId, JsCompilerOutputPackagingElement.class);
        }

        @Override
        public JsCompilerOutputPackagingElement load(Element element) {
            JpsModuleReference moduleReference = JpsFacetSerializer.createModuleReference("TODO resolve module");
            return new JsCompilerOutputPackagingElement(moduleReference);
        }

        @Override
        public void save(JsCompilerOutputPackagingElement element, Element tag) {
            //String id = JpsFacetSerializer.getFacetId(element.getModuleReference(), GWT_FACET_ID, GWT_FACET_NAME);
            //tag.setAttribute(PACKAGING_FACET_ATTRIBUTE, id);
        }
    }
}
