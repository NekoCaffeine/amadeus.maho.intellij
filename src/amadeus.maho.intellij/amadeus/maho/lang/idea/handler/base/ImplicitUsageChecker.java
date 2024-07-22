package amadeus.maho.lang.idea.handler.base;

import java.util.List;
import java.util.Set;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.impl.analysis.LocalRefUseInfo;
import com.intellij.codeInspection.canBeFinal.CanBeFinalHandler;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.MultiMap;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.idea.light.LightElement;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class ImplicitUsageChecker extends CanBeFinalHandler implements ImplicitUsageProvider {
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class RefData {
        
        LocalRefUseInfo inner;
        
        public MultiMap<PsiElement, PsiReference> localRefMap() = (Privilege) inner.myLocalRefsMap;
        
    }
    
    private static @Nullable RefData refData(final PsiElement element) {
        if (element instanceof PsiCompiledElement)
            return null;
        @Nullable PsiFile file = PsiTreeUtil.getParentOfType(element, PsiFile.class);
        if (file == null && element instanceof LightElement lightElement)
            file = ~lightElement.equivalents().stream()
                    .map(it -> PsiTreeUtil.getParentOfType(it, PsiFile.class))
                    .nonnull();
        if (file == null)
            return null;
        return { LocalRefUseInfo.forFile(file) };
    }
    
    @Override
    public boolean isImplicitUsage(final PsiElement element) {
        final @Nullable RefData refData = refData(element);
        return refData != null && (
                Handler.Marker.baseHandlers().stream().anyMatch(handler -> handler.isImplicitUsage(element, refData) || handler.isImplicitRead(element, refData)) ||
                Syntax.Marker.syntaxHandlers().values().stream().anyMatch(handler -> handler.isImplicitUsage(element, refData) || handler.isImplicitRead(element, refData))
        );
    }
    
    @Override
    public boolean isImplicitRead(final PsiElement element) {
        final @Nullable RefData refData = refData(element);
        return refData != null && (
                Handler.Marker.baseHandlers().stream().anyMatch(handler -> handler.isImplicitRead(element, refData)) ||
                Syntax.Marker.syntaxHandlers().values().stream().anyMatch(handler -> handler.isImplicitRead(element, refData))
        );
    }
    
    @Override
    public boolean isImplicitWrite(final PsiElement element) {
        final @Nullable RefData refData = refData(element);
        return refData != null && (
                Handler.Marker.baseHandlers().stream().anyMatch(handler -> handler.isImplicitWrite(element, refData)) ||
                Syntax.Marker.syntaxHandlers().values().stream().anyMatch(handler -> handler.isImplicitWrite(element, refData))
        );
    }
    
    @Override
    public boolean canBeFinal(final PsiMember member) = !isImplicitWrite(member);
    
    @Hook(value = DefUseUtil.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    public static void getUnusedDefs(final @Nullable List<DefUseUtil.Info> capture, final PsiElement body, final Set<? super PsiVariable> outUsedVariables)
            = capture?.removeIf(info -> Handler.Marker.baseHandlers().stream().anyMatch(handler -> handler.isVariableOut(info.getVariable())) ||
                                        Syntax.Marker.syntaxHandlers().values().stream().anyMatch(handler -> handler.isVariableOut(info.getVariable())));
    
}
