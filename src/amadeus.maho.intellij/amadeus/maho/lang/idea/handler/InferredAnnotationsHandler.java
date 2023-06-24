package amadeus.maho.lang.idea.handler;

import java.util.List;

import com.intellij.codeInsight.DefaultInferredAnnotationProvider;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.CachedValuesManager;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;

// @TransformProvider // FIXME impl
public class InferredAnnotationsHandler {
    
    @Hook
    private static Hook.Result ignoreInference(final DefaultInferredAnnotationProvider $this, final PsiModifierListOwner owner, final @Nullable String annotationFQN) = Hook.Result.falseToVoid(annotationFQN?.endsWith(".NotNull") ?? false);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static void findInferredAnnotations(final List<PsiAnnotation> capture, final DefaultInferredAnnotationProvider $this, final PsiModifierListOwner owner) = capture *= inferredAnnotations(owner);
    
    public static List<PsiAnnotation> inferredAnnotations(final PsiModifierListOwner owner) = CachedValuesManager.getProjectPsiDependentCache(owner, it -> List.of());
    
}
