package amadeus.maho.lang.idea.handler;

import java.lang.reflect.Method;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightMethodUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.util.CachedValuesManager;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.LookupHelper;
import amadeus.maho.util.llm.LLM;
import amadeus.maho.util.llm.LLMApi;

@TransformProvider
public interface LLMHandler {
    
    @Hook(value = HighlightMethodUtil.class, isStatic = true)
    private static Hook.Result checkMethodMustHaveBody(final PsiMethod method, final @Nullable PsiClass containingClass) = Hook.Result.falseToVoid(method.hasAnnotation(LLM.class.getCanonicalName()), null);
    
    Method invokeDefaultInstanceMethod = LookupHelper.<Method, Object[], Object>method2(LLMApi::invokeDefaultInstance);
    
    String bodyText = STR."{ return \{invokeDefaultInstanceMethod.getDeclaringClass().getCanonicalName()}.\{invokeDefaultInstanceMethod.getName()}(); }";
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static PsiCodeBlock getBody(final @Nullable PsiCodeBlock capture, final PsiMethodImpl $this) = capture ?? CachedValuesManager.getProjectPsiDependentCache($this,
            method -> method.hasAnnotation(LLM.class.getCanonicalName()) ? PsiElementFactory.getInstance($this.getProject()).createCodeBlockFromText(bodyText, $this) : null);
    
}
