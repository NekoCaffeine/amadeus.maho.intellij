package amadeus.maho.lang.idea.handler;

import java.lang.annotation.Repeatable;

import com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class RepeatableHandler {
    
    @Hook(value = AnnotationsHighlightUtil.class, isStatic = true)
    private static Hook.Result checkMissingAttributes(final PsiAnnotation annotation)
            = Hook.Result.falseToVoid(annotation.getNameReferenceElement()?.resolve() ?? null instanceof PsiClass psiClass && Repeatable.class.getCanonicalName().equals(psiClass.getQualifiedName()), null);
    
}
