package amadeus.maho.lang.idea.handler;

import java.lang.annotation.Annotation;
import java.util.stream.Stream;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiTypeElement;

import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;

@Handler(Hook.class)
public class HookHandler extends BaseHandler {
    
    @Override
    public void check(final PsiElement tree, final Annotation annotation, final PsiAnnotation annotationTree, final ProblemsHolder holder, final QuickFixFactory quickFix) {
        if (tree instanceof PsiMethod method) {
            final @Nullable PsiTypeElement returnTypeElement = method.getReturnTypeElement();
            if (returnTypeElement != null) {
                if (!method.hasModifierProperty(PsiModifier.STATIC))
                    holder.registerProblem(returnTypeElement, "The target method of the @%s must be static.".formatted(Hook.class.getSimpleName()), ProblemHighlightType.GENERIC_ERROR,
                            quickFix.createModifierListFix(method, PsiModifier.STATIC, true, false));
                if (switch (method.getReturnType()) {
                    case PsiClassType classType -> !(classType.resolve()?.getQualifiedName()?.equals(Hook.Result.class.getCanonicalName()) ?? false);
                    case null, default          -> true;
                } && Stream.of(method.getParameterList().getParameters()).anyMatch(parameter -> parameter.hasAnnotation(Hook.Reference.class.getCanonicalName())))
                    holder.registerProblem(returnTypeElement, "@%s needs to pass the result by returning value type %s.".formatted(Hook.Reference.class.getSimpleName(), Hook.Result.class.getSimpleName()), ProblemHighlightType.GENERIC_ERROR,
                            quickFix.createMethodReturnFix(method, JavaPsiFacade.getElementFactory(method.getProject()).createTypeByFQClassName(Hook.Result.class.getCanonicalName(), method.getResolveScope()), false));
            }
        }
    }
    
}
