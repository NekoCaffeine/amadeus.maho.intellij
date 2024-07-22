package amadeus.maho.lang.idea.handler;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightClassUtil;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;

import amadeus.maho.lang.Unsupported;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

import static amadeus.maho.lang.idea.handler.UnsupportedHandler.PRIORITY;

@TransformProvider
@Handler(value = Unsupported.class, priority = PRIORITY)
public class UnsupportedHandler extends BaseHandler<Unsupported> {
    
    public static final int PRIORITY = ConstructorHandler.PRIORITY << 4;
    
    @Override
    public void check(final PsiElement tree, final Unsupported annotation, final PsiAnnotation annotationTree, final ProblemsHolder holder, final QuickFixFactory quickFix) {
        if (tree instanceof PsiClass context && context.hasModifierProperty(PsiModifier.ABSTRACT))
            holder.registerProblem(annotationTree, "@Unsupported cannot be marked on an abstract class or interface", ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
    }
    
    @Hook(value = HighlightClassUtil.class, isStatic = true)
    private static Hook.Result checkClassWithAbstractMethods(final PsiClass owner, final PsiElement implementsFixElement, final TextRange range) = Hook.Result.falseToVoid(owner.hasAnnotation(Unsupported.class.getCanonicalName()), null);
    
}
