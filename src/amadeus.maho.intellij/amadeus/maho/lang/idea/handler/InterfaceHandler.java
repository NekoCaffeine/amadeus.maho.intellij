package amadeus.maho.lang.idea.handler;

import java.util.Set;

import com.intellij.codeInsight.completion.JavaMemberNameCompletionContributor;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightClassUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.PsiClassInitializerImpl;

import amadeus.maho.lang.Privilege;
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
    
    @Hook(value = JavaMemberNameCompletionContributor.class, isStatic = true, at = @At(method = @At.MethodInsn(name = "completeVariableNameForRefactoring")))
    private static void completeFieldName(final Set<LookupElement> set, final PsiField var, final PrefixMatcher matcher, final boolean includeOverlapped) {
        (Privilege) JavaMemberNameCompletionContributor.completeVariableNameForRefactoring(var.getProject(), set, matcher, var.getType(), VariableKind.LOCAL_VARIABLE, includeOverlapped, true);
    }
    
}
