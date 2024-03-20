package amadeus.maho.lang.idea.handler;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.varScopeCanBeNarrowed.ParameterCanBeLocalInspection;
import com.intellij.psi.PsiMethod;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class ParameterCanBeLocalHandler {
    
    private static final Hook.Result empty = { ProblemDescriptor.EMPTY_ARRAY };
    
    @Hook
    private static Hook.Result checkMethod(final ParameterCanBeLocalInspection $this, final PsiMethod method, final InspectionManager manager, final boolean isOnTheFly)
            = method.hasAnnotation(Hook.class.getCanonicalName()) ? empty : Hook.Result.VOID;
    
}
