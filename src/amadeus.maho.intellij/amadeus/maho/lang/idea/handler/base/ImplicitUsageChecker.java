package amadeus.maho.lang.idea.handler.base;

import java.util.List;
import java.util.Set;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.analysis.RefCountHolder;
import com.intellij.codeInspection.canBeFinal.CanBeFinalHandler;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
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
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.idea.light.LightElement;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class ImplicitUsageChecker extends CanBeFinalHandler implements ImplicitUsageProvider {
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class RefData {
        
        RefCountHolder inner;
        
        public MultiMap<PsiElement, PsiReference> localRefMap() = (Privilege) inner.myLocalRefsMap;
        
    }
    
    private static @Nullable RefData refData(final PsiElement element) {
        @Nullable PsiFile file = PsiTreeUtil.getParentOfType(element, PsiFile.class);
        if (file == null && element instanceof LightElement lightElement)
            file = lightElement.equivalents().stream()
                    .map(it -> PsiTreeUtil.getParentOfType(it, PsiFile.class))
                    .nonnull()
                    .findFirst()
                    .orElse(null);
        if (file == null)
            return null;
        final Project project = element.getProject();
        final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        final @Nullable TextRange dirtyScope = document == null ? null : DaemonCodeAnalyzerEx.getInstanceEx(project).getFileStatusMap().getFileDirtyScope(document, file, com.intellij.codeHighlighting.Pass.UPDATE_ALL);
        return { (Privilege) RefCountHolder.get(file, dirtyScope ?? file.getTextRange()) };
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
        final @Nullable RefData helper = refData(element);
        return helper != null && (
                Handler.Marker.baseHandlers().stream().anyMatch(handler -> handler.isImplicitRead(element, helper)) ||
                Syntax.Marker.syntaxHandlers().values().stream().anyMatch(handler -> handler.isImplicitRead(element, helper))
        );
    }
    
    @Override
    public boolean isImplicitWrite(final PsiElement element) {
        final @Nullable RefData helper = refData(element);
        return helper != null && (
                Handler.Marker.baseHandlers().stream().anyMatch(handler -> handler.isImplicitWrite(element, helper)) ||
                Syntax.Marker.syntaxHandlers().values().stream().anyMatch(handler -> handler.isImplicitWrite(element, helper))
        );
    }
    
    @Override
    public boolean canBeFinal(final PsiMember member) = !isImplicitWrite(member);
    
    @Hook(value = DefUseUtil.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    public static void getUnusedDefs(final @Nullable List<DefUseUtil.Info> capture, final PsiElement body, final Set<? super PsiVariable> outUsedVariables)
            = capture?.removeIf(info -> Handler.Marker.baseHandlers().stream().anyMatch(handler -> handler.isVariableOut(info.getVariable())) ||
                                        Syntax.Marker.syntaxHandlers().values().stream().anyMatch(handler -> handler.isVariableOut(info.getVariable())));
    
}
