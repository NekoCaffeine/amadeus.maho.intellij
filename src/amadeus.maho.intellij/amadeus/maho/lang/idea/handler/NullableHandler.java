package amadeus.maho.lang.idea.handler;

import java.util.List;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.NullableNotNullManagerImpl;
import com.intellij.codeInsight.annoPackages.AnnotationPackageSupport;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.SmartTypePointer;
import com.intellij.psi.TypeAnnotationProvider;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.refactoring.introduceVariable.VariableExtractor;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class NullableHandler implements AnnotationPackageSupport {
    
    @Getter
    private static final NullableHandler instance = { };
    
    private static final String annotationName = Nullable.class.getCanonicalName();
    
    @Override
    public List<String> getNullabilityAnnotations(final Nullability nullability) = switch (nullability) {
        case NULLABLE -> List.of(annotationName);
        default       -> List.of();
    };
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), exactMatch = false)
    private static void _init_(final NullableNotNullManagerImpl $this) = $this.myDefaultNullable = annotationName;
    
    @Hook(value = AddAnnotationPsiFix.class, isStatic = true)
    public static Hook.Result addPhysicalAnnotationIfAbsent(final String fqn, final PsiNameValuePair pairs[], final PsiAnnotationOwner owner)
            = Hook.Result.falseToVoid(owner instanceof PsiModifierList modifierList && NullableNotNullManager.getInstance(modifierList.getProject()).getNotNulls().contains(fqn), null);
    
    @Hook(value = PsiJavaCodeReferenceElementImpl.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static JavaResolveResult[] tryClassResult(final JavaResolveResult capture[], final String qualifiedName, final PsiJavaCodeReferenceElement referenceElement) {
        if (capture.length == 0 && (qualifiedName.equals("Nullable") || qualifiedName.endsWith(".Nullable")) && !qualifiedName.equals(annotationName)) {
            final @Nullable PsiFile containingFile = referenceElement.getContainingFile();
            if (containingFile != null && containingFile.isPhysical()) {
                final VirtualFile virtualFile = containingFile.getVirtualFile();
                if (virtualFile != null && LibraryUtil.findLibraryEntry(virtualFile, containingFile.getProject()) != null) {
                    final @Nullable PsiClass candidate = JavaPsiFacade.getInstance(referenceElement.getProject()).findClass(annotationName, referenceElement.getResolveScope());
                    if (candidate != null) {
                        final CandidateInfo info = { candidate, PsiSubstitutor.EMPTY, referenceElement, false };
                        return { info };
                    }
                }
            }
        }
        return capture;
    }
    
    // dfa nullability -> extract variable
    @Hook(value = VariableExtractor.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static PsiType stripNullabilityAnnotationsFromTargetType(final PsiType capture, final SmartTypePointer selectedType, final PsiExpression expression) {
        final Project project = expression.getProject();
        if (capture.getAnnotations().length == 0 && NullabilityUtil.getExpressionNullability(expression, true) == Nullability.NULLABLE)
            return capture.annotate(TypeAnnotationProvider.Static.create(new PsiAnnotation[]{
                    JavaPsiFacade.getElementFactory(project).createAnnotationFromText(STR."@\{NullableNotNullManager.getInstance(project).getDefaultNullable()}", expression)
            }));
        return capture;
    }
    
}
