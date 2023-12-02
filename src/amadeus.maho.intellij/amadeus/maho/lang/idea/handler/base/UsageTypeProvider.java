package amadeus.maho.lang.idea.handler.base;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiMethod;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.impl.rules.JavaUsageTypeProvider;
import com.intellij.usages.impl.rules.UsageType;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface UsageTypeProvider {
    
    UsageType
            associationMethod   = { () -> "Association method" },
            operatorOverloading = { () -> "Operator Overloading" };
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static @Nullable UsageType getUsageType(final @Nullable UsageType capture, final JavaUsageTypeProvider $this, final PsiElement element, final UsageTarget targets[])
            = capture ?? switch (element) {
        case PsiMethod method   -> associationMethod;
        case PsiJavaToken token -> operatorOverloading;
        default                 -> null;
    };
    
}
