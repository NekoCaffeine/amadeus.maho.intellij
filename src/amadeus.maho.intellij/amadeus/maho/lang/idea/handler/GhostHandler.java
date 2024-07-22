package amadeus.maho.lang.idea.handler;

import java.lang.reflect.Method;

import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.util.CachedValuesManager;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.GhostContext;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.annotation.mark.Ghost;
import amadeus.maho.util.dynamic.LookupHelper;

@TransformProvider
public interface GhostHandler {
    
    Method touchMethod = LookupHelper.method0(GhostContext::touch);
    
    String bodyText = STR."{ throw \{touchMethod.getDeclaringClass().getCanonicalName()}.\{touchMethod.getName()}(); }";
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static PsiCodeBlock getBody(final @Nullable PsiCodeBlock capture, final PsiMethodImpl $this) = capture ?? CachedValuesManager.getProjectPsiDependentCache($this,
            method -> method.hasAnnotation(Ghost.class.getCanonicalName()) ? PsiElementFactory.getInstance($this.getProject()).createCodeBlockFromText(bodyText, $this) : null);
    
}
