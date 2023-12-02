package amadeus.maho.lang.idea.handler.base;

import java.util.stream.Stream;

import com.intellij.ide.structureView.StructureViewExtension;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;

import amadeus.maho.lang.idea.light.LightElement;
import amadeus.maho.lang.inspection.Nullable;

// Used to display the injected dummy symbols in the structure UI
public class StructureInjecter implements StructureViewExtension {
    
    @Override
    public Class<? extends PsiElement> getType() = PsiClass.class;
    
    @Override
    public StructureViewTreeElement[] getChildren(final PsiElement parent)
            = Stream.concat(Stream.concat(Stream.of(((PsiClass) parent).getInnerClasses()), Stream.of(((PsiClass) parent).getMethods())), Stream.of(((PsiClass) parent).getFields()))
            .filter(LightElement.class::isInstance)
            .map(element -> (PsiElement & LightElement) element)
            .map(StructureElement::new)
            .toArray(StructureViewTreeElement[]::new);
    
    @Override
    public @Nullable Object getCurrentEditorElement(final Editor editor, final PsiElement element) = null;
    
}
