package amadeus.maho.lang.idea.handler;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.formatter.java.JavaFormatterUtil;
import com.intellij.psi.util.PsiTreeUtil;

import amadeus.maho.lang.idea.IDEAContext;
import amadeus.maho.lang.inspection.TypeModifier;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class TypeModifierHandler {
    
    @Hook(value = JavaFormatterUtil.class, isStatic = true)
    private static Hook.Result isTypeAnnotationOrFalseIfDumb(final ASTNode child) {
        final PsiElement element = child.getPsi();
        return !(element.getProject().isDefault() || PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace.class, PsiAnnotation.class) instanceof PsiKeyword) &&
                !DumbService.isDumb(element.getProject()) && element instanceof PsiAnnotation annotation && (AnnotationTargetUtil.isTypeAnnotation(annotation) || IDEAContext.marked(annotation, TypeModifier.class.getCanonicalName())) ?
                Hook.Result.TRUE : Hook.Result.FALSE;
    }
    
}
