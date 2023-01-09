package amadeus.maho.lang.idea.handler;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightMethodUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;

import amadeus.maho.lang.Default;
import amadeus.maho.lang.VisitorChain;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.ExtensibleMembers;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.light.LightField;
import amadeus.maho.lang.idea.light.LightLiteralExpression;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

import static amadeus.maho.lang.idea.handler.VisitorChainHandler.PRIORITY;
import static com.intellij.psi.PsiModifier.*;

@TransformProvider
@Handler(value = VisitorChain.class, priority = PRIORITY)
public class VisitorChainHandler extends BaseHandler<VisitorChain> {

    public static final int PRIORITY = -1 << 10;

    @Override
    public void processClass(final PsiClass tree, final VisitorChain annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) {
        if (tree != context)
            return;
        final PsiClassType classType = JavaPsiFacade.getElementFactory(tree.getProject()).createType(tree);
        final LightField visitor = { tree, "visitor", classType, annotationTree };
        final LightLiteralExpression expression = { tree.getManager(), classType };
        visitor.setInitializer(expression);
        visitor.setNavigationElement(annotationTree);
        visitor.setContainingClass(context);
        visitor.addModifiers(PROTECTED, FINAL);
        visitor.getModifierList().addAnnotation(Default.class.getCanonicalName());
        visitor.getModifierList().addAnnotation(Nullable.class.getCanonicalName());
        members.inject(visitor);
    }

    @Hook(value = HighlightMethodUtil.class, isStatic = true)
    private static Hook.Result checkMethodMustHaveBody(final PsiMethod method, final @Nullable PsiClass containingClass) = Hook.Result.falseToVoid(containingClass?.hasAnnotation(VisitorChain.class.getCanonicalName()) ?? false, null);

}
