package amadeus.maho.lang.idea.light;

import java.util.List;
import java.util.function.Predicate;

import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.SyntheticElement;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface LightElement extends ModificationTracker, SyntheticElement {
    
    @Override
    default long getModificationCount() = 0L;
    
    default List<PsiElement> equivalents() = List.of();
    
    default boolean virtual() = false;
    
    default @Nullable String mark() = null;
    
    default @Nullable String location() {
        final @Nullable String mark = mark();
        return mark != null ? mark : equivalents().stream()
                .filter(PsiAnnotation.class::isInstance)
                .map(PsiAnnotation.class::cast)
                .findFirst()
                .map(PsiAnnotation::getText)
                .orElse(null);
    }
    
    static boolean equivalentTo(final LightElement light, final PsiElement element) = light.equivalents().stream().anyMatch(it -> it.isEquivalentTo(element));
    
    @Hook(value = OverrideImplementExploreUtil.class, isStatic = true, at = @At(method = @At.MethodInsn(name = "findMethodBySignature")), before = false, capture = true)
    private static @Nullable PsiMethod getMapToOverrideImplement(final PsiMethod capture, final PsiClass aClass, final boolean toImplement, final boolean skipImplemented) = capture instanceof LightElement ? null : capture;
    
    @Hook(value = OverrideImplementUtil.class, isStatic = true, at = @At(method = @At.MethodInsn(name = "removeIf")), capture = true)
    private static @Nullable Predicate<PsiMethod> overrideOrImplementMethod(final Predicate<PsiMethod> capture, final PsiClass aClass, final PsiMethod method, final PsiSubstitutor substitutor,
            final boolean toCopyJavaDoc, final boolean insertOverrideIfPossible) = it -> {
        final PsiMethod find = aClass.findMethodBySignature(it, false);
        return find != null && !(find instanceof LightElement);
    };
    
}
