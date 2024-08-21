package amadeus.maho.lang.idea.handler;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightClassUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.llm.LLM;

@TransformProvider
public interface LLMHandler {
    
    @Hook(value = HighlightClassUtil.class, isStatic = true)
    private static Hook.Result checkClassWithAbstractMethods(final PsiClass owner, final PsiElement implementsFixElement, final TextRange range) = Hook.Result.falseToVoid(owner.hasAnnotation(LLM.class.getCanonicalName()), null);
    
}
