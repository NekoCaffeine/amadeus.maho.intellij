package amadeus.maho.lang.idea.handler;

import java.util.Set;

import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.JavaPlatformModuleSystem;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.psi.impl.compiled.ClsModifierListImpl;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.resolve.PsiResolveHelperImpl;
import com.intellij.psi.search.GlobalSearchScope;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

import static com.intellij.psi.PsiModifier.*;

@TransformProvider
public class AccessibleHandler {
    
    @Hook
    private static Hook.Result inAddedExports(final JavaPlatformModuleSystem $this, final Module module, final String targetName, final String packageName, final String useName) = Hook.Result.TRUE;
    
    @Hook
    private static Hook.Result inAddedModules(final JavaPlatformModuleSystem $this, final Module module, final String moduleName) = Hook.Result.TRUE;
    
    @Hook(value = JavaModuleGraphUtil.class, isStatic = true)
    private static Hook.Result exports(final PsiJavaModule source, final String packageName, final @Nullable PsiJavaModule target) = Hook.Result.TRUE;
    
    @Hook(value = JavaModuleGraphUtil.class, isStatic = true)
    private static Hook.Result reads(final PsiJavaModule source, final PsiJavaModule destination) = Hook.Result.falseToVoid(destination instanceof LightJavaModule);
    
    @Hook(value = JavaResolveUtil.class, isStatic = true)
    private static Hook.Result isAccessible(final PsiMember member, final @Nullable PsiClass memberClass, final @Nullable PsiModifierList modifierList, final PsiElement place,
            final @Nullable PsiClass accessObjectClass, final @Nullable PsiElement currentFileResolveScope, final @Nullable PsiFile placeFile) = Hook.Result.falseToVoid(member instanceof PsiClass || PrivilegeHandler.inPrivilege(place));
    
    @Hook
    private static Hook.Result isAccessible(final PsiResolveHelperImpl $this, final PsiPackage pkg, final PsiElement place) = Hook.Result.TRUE;
    
    @Hook(value = JavaCompletionUtil.class, isStatic = true)
    private static Hook.Result isSourceLevelAccessible(final PsiElement context, final PsiClass target, final boolean pkgContext, final @Nullable PsiClass qualifierClass) = switch (context) {
        case PsiMember ignored -> {
            final @Nullable PsiJavaModule sourceModule = JavaModuleGraphUtil.findDescriptorByElement(context.getContainingFile().getOriginalFile()); // see: com.intellij.codeInsight.completion.CompletionParameters.getPosition
            if (sourceModule != null) {
                final @Nullable PsiJavaModule targetModule = JavaModuleGraphUtil.findDescriptorByElement(target);
                if (targetModule != null)
                    yield Hook.Result.trueToVoid(JavaModuleGraphUtil.reads(sourceModule, targetModule));
            }
            yield Hook.Result.VOID;
        }
        case null, default     -> Hook.Result.VOID;
    };
    
    public static Set<String> transformPackageLocalToProtected(final PsiElement parent, final Set<String> modifiers) {
        if ((parent instanceof PsiMethod || parent instanceof PsiField) && modifiers.contains(PACKAGE_LOCAL)) {
            modifiers -= PACKAGE_LOCAL;
            modifiers += PROTECTED;
        }
        return modifiers;
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static boolean hasModifierProperty(final boolean capture, final ClsModifierListImpl $this, final String name)
            = capture && (!($this.getParent() instanceof PsiMethod || $this.getParent() instanceof PsiField) || !PACKAGE_LOCAL.equals(name)) ||
              ($this.getParent() instanceof PsiMethod || $this.getParent() instanceof PsiField) && PROTECTED.equals(name) && ModifierFlags.hasModifierProperty(PACKAGE_LOCAL, $this.getStub().getModifiersMask());
    
    @Hook(value = PsiClassImplUtil.class, isStatic = true, forceReturn = true)
    private static <T extends PsiType> @Nullable T correctType(final @Nullable T originalType, final GlobalSearchScope scope) = originalType;
    
}
