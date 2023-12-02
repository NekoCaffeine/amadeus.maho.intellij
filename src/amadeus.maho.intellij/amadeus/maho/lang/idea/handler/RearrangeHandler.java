package amadeus.maho.lang.idea.handler;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.CachedValuesManager;

import amadeus.maho.lang.Rearrange;
import amadeus.maho.lang.idea.handler.base.HandlerSupport;
import amadeus.maho.lang.idea.light.LightField;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.tuple.Tuple2;

@TransformProvider
public class RearrangeHandler {
    
    public static @Nullable Function<String, PsiField> rearrangeFunction(final PsiClass psiClass) = CachedValuesManager.getProjectPsiDependentCache(psiClass, it -> psiClass.isRecord() ?
            HandlerSupport.getAnnotationsByType(it, Rearrange.class).stream().findFirst().map(Tuple2::v1).map(rearrange -> {
                final String alias[] = rearrange.alias();
                final List<? extends PsiClass> classes = rearrange.accessPsiClasses(Rearrange::adapters).map(PsiClassType::resolve).nonnull().filter(PsiClass::isRecord).toList();
                final ConcurrentHashMap<String, PsiField> cacheMap = { };
                return (Function<String, PsiField>) name -> cacheMap.computeIfAbsent(name, key -> Stream.of(alias).map(string -> {
                    final long count = name.codePoints().count();
                    final int codePoints[] = string.codePoints().toArray();
                    if (count == name.codePoints()
                            .map(codePoint -> ArrayHelper.indexOf(codePoints, codePoint))
                            .takeWhile(index -> index > -1).count()) {
                        final @Nullable PsiClass target = psiClass.getRecordComponents().length == count ? psiClass : classes.stream().filter(candidate -> candidate.getRecordComponents().length == count).findFirst().orElse(null);
                        if (target != null) {
                            final LightField fakeFiled = { it, name, JavaPsiFacade.getElementFactory(it.getProject()).createType(target) };
                            fakeFiled.setContainingClass(it);
                            return fakeFiled;
                        }
                    }
                    return null;
                }).nonnull().findFirst().orElse(null));
            }).orElse(null) : null);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static JavaResolveResult[] resolveToVariable(final JavaResolveResult capture[], final PsiReferenceExpressionImpl $this, final PsiFile containingFile) {
        if (capture.length == 0 && $this.getQualifierExpression()?.getType() ?? null instanceof PsiClassType classType) {
            final @Nullable PsiClass resolved = classType.resolve();
            if (resolved != null) {
                final @Nullable String name = $this.getReferenceName();
                if (name != null) {
                    final @Nullable PsiField fakeField = rearrangeFunction(resolved)?.apply(name) ?? null;
                    if (fakeField != null) {
                        final CandidateInfo info = { fakeField, PsiSubstitutor.EMPTY, false, false, containingFile };
                        return { info };
                    }
                }
            }
        }
        return capture;
    }
    
}
