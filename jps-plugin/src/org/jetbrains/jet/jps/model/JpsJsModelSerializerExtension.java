package org.jetbrains.jet.jps.model;

import org.jdom.Element;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.artifact.JpsPackagingElementSerializer;
import org.jetbrains.jps.model.serialization.facet.JpsFacetSerializer;

import java.util.Collections;
import java.util.List;

public class JpsJsModelSerializerExtension extends JpsModelSerializerExtension {
    @Override
    public List<? extends JpsPackagingElementSerializer<?>> getPackagingElementSerializers() {
        return Collections.singletonList(new JpsJsCompilerOutputElementSerializer(JsExternalizationConstants.COMPILER_OUTPUT_ELEMENT_ID));
    }

    private static class JpsJsCompilerOutputElementSerializer extends JpsPackagingElementSerializer<JpsJsCompilerOutputPackagingElement> {
        public JpsJsCompilerOutputElementSerializer(String typeId) {
            super(typeId, JpsJsCompilerOutputPackagingElement.class);
        }

        @Override
        public JpsJsCompilerOutputPackagingElement load(Element element) {
            JpsModuleReference moduleReference = JpsFacetSerializer.createModuleReference("TODO resolve module");
            return new JpsJsCompilerOutputPackagingElement(moduleReference);
        }

        @Override
        public void save(JpsJsCompilerOutputPackagingElement element, Element tag) {
            //String id = JpsFacetSerializer.getFacetId(element.getModuleReference(), GWT_FACET_ID, GWT_FACET_NAME);
            //tag.setAttribute(PACKAGING_FACET_ATTRIBUTE, id);
        }
    }
}
