package amadeus.maho.lang.idea.handler;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightClassUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.PsiClassInitializerImpl;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

import static com.intellij.psi.PsiModifier.STATIC;

@TransformProvider
public class InterfaceHandler {
    
    @Hook(value = HighlightClassUtil.class, isStatic = true)
    private static Hook.Result checkThingNotAllowedInInterface(final PsiElement element, final @Nullable PsiClass psiClass) = Hook.Result.falseToVoid(element instanceof PsiClassInitializer, null);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static boolean hasModifierProperty(final boolean capture, final PsiClassInitializerImpl $this, final String name) = capture || STATIC.equals(name) && $this.getParent() instanceof PsiClass parent && parent.isInterface();
    
}
