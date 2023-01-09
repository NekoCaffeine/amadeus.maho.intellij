package amadeus.maho.lang.idea.handler;

import java.util.List;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.miscGenerics.RedundantTypeArgsInspection;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.LookupHelper;

@TransformProvider
public class LookupHelperHandler {
    
    @Hook(value = RedundantTypeArgsInspection.class, isStatic = true)
    private static Hook.Result checkCallExpression(final PsiJavaCodeReferenceElement reference, final PsiType typeArguments[], final PsiCallExpression expression, final InspectionManager inspectionManager,
            final List<? super ProblemDescriptor> problems, final boolean isOnTheFly) = Hook.Result.falseToVoid(reference.advancedResolve(false).getElement() instanceof PsiMethod method &&
            method.getContainingClass()?.getQualifiedName()?.equals(LookupHelper.class.getCanonicalName()) ?? false, null);
    
}
