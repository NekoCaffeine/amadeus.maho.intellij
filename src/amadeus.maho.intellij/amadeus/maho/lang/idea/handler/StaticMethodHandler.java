package amadeus.maho.lang.idea.handler;

import java.util.List;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightMethodUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class StaticMethodHandler {
    
    @Hook(isStatic = true, value = HighlightMethodUtil.class)
    private static Hook.Result checkMethodIncompatibleReturnType(final MethodSignatureBackedByPsiMethod method, final List<? extends HierarchicalMethodSignature> signatures,
            final boolean includeRealPositionInfo, @Nullable final TextRange textRange, final @Nullable Ref<? super String> description) = Hook.Result.falseToVoid(method.getMethod().hasModifierProperty(PsiModifier.STATIC), null);
    
}
