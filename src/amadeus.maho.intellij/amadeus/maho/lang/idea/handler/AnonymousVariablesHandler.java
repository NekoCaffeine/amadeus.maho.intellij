package amadeus.maho.lang.idea.handler;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.psi.PsiVariable;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class AnonymousVariablesHandler {
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkVariableAlreadyDefined(final PsiVariable variable) = Hook.Result.falseToVoid(variable.getName()?.equals("_") ?? false, null);
    
}
