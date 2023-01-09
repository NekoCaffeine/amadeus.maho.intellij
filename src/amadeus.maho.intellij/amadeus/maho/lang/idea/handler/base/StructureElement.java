package amadeus.maho.lang.idea.handler.base;

import java.util.Collection;
import java.util.List;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.java.JavaClassTreeElement;
import com.intellij.ide.structureView.impl.java.JavaClassTreeElementBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.util.ui.UIUtil;

import amadeus.maho.lang.idea.light.LightElement;
import amadeus.maho.lang.inspection.Nullable;

import static com.intellij.psi.util.PsiFormatUtil.*;

public class StructureElement<V extends PsiElement & LightElement> extends JavaClassTreeElementBase<V> {
    
    public StructureElement(final V value) = super(false, value);
    
    @Override
    public String getPresentableText() {
        try {
            final @Nullable PsiElement element = getElement();
            if (element == null)
                return "<invalid>";
            if (element instanceof PsiClass target)
                return target.getName();
            final boolean dumb = DumbService.isDumb(element.getProject());
            if (element instanceof PsiField target)
                return formatVariable(target, SHOW_NAME | (dumb ? 0 : SHOW_TYPE) | TYPE_AFTER | (dumb ? 0 : SHOW_INITIALIZER), PsiSubstitutor.EMPTY).replace(":", ": ");
            if (element instanceof PsiMethod target)
                return formatMethod(target, PsiSubstitutor.EMPTY, SHOW_NAME | TYPE_AFTER | SHOW_PARAMETERS | (dumb ? 0 : SHOW_TYPE), dumb ? SHOW_NAME : SHOW_TYPE).replace(":", ": ");
            return element.getText();
        } catch (final PsiInvalidElementAccessException e) { return "<invalid>"; }
    }
    
    @Override
    public String getLocationString() = " " + UIUtil.rightArrow() + " " + (getValue().virtual() ? "(virtual) " : "") + getValue().equivalents().stream()
            .filter(PsiAnnotation.class::isInstance)
            .map(PsiAnnotation.class::cast)
            .findFirst()
            .map(PsiAnnotation::getText)
            .orElseGet(getValue()::mark);
    
    @Override
    public Collection<StructureViewTreeElement> getChildrenBase() = getElement() instanceof PsiClass psiClass ? new JavaClassTreeElement(psiClass, false).getChildrenBase() : List.of();
    
    @Override
    public String getLocationPrefix() = "";
    
    @Override
    public String getLocationSuffix() = "";
    
}
