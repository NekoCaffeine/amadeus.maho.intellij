package amadeus.maho.lang.idea.handler;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.stream.Stream;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PsiTypeElementImpl;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface AnnotationHandler {
    
    @Hook
    private static Hook.Result visitClassType(final AnnotationsHighlightUtil.AnnotationReturnTypeVisitor $this,
            final PsiClassType classType) = Hook.Result.falseToVoid(classType.equalsToText(CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION));
    
    @Hook(isStatic = true, value = AnnotationsHighlightUtil.class)
    private static Hook.Result checkMemberValueType(final PsiAnnotationMemberValue value, final PsiType expectedType, final PsiMethod method) {
        if (expectedType instanceof PsiClassType classType) {
            final @Nullable PsiClass resolve = classType.resolve();
            if (resolve != null) {
                final @Nullable String name = resolve.getQualifiedName();
                if (name != null && name.equals(Annotation.class.getCanonicalName()) && value instanceof PsiAnnotation)
                    return Hook.Result.NULL;
            }
        } else if (expectedType instanceof PsiArrayType arrayType) {
            if (arrayType.getComponentType() instanceof PsiClassType classType) {
                final @Nullable PsiClass resolve = classType.resolve();
                if (resolve != null) {
                    final @Nullable String name = resolve.getQualifiedName();
                    if (name != null && name.equals(Annotation.class.getCanonicalName()) &&
                            (value instanceof PsiAnnotation || value instanceof PsiArrayInitializerExpression initializer && Stream.of(initializer.getInitializers()).allMatch(PsiAnnotation.class::isInstance)))
                        return Hook.Result.NULL;
                }
            }
        }
        return Hook.Result.VOID;
    }
    
    @Hook(value = AnnotationUtil.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static @Nullable List<PsiAnnotation> findOwnAnnotations(final @Nullable List<PsiAnnotation> capture, final PsiModifierListOwner owner, final Iterable<String> annotationNames) {
        if (owner instanceof PsiMethod method && method.getReturnTypeElement() instanceof PsiTypeElementImpl typeElement && typeElement.getType() instanceof PsiArrayType arrayType) {
            final @Nullable List<PsiAnnotation> annotations = findOwnAnnotations(arrayType.getDeepComponentType(), method.getModifierList(), annotationNames);
            if (capture == null)
                return annotations;
            if (annotations != null)
                capture *= annotations;
        }
        return capture;
    }
    
    private static @Nullable List<PsiAnnotation> findOwnAnnotations(final PsiAnnotationOwner owner, final PsiModifierList applicableTarget, final Iterable<String> annotationNames) {
        @Nullable SmartList<PsiAnnotation> result = null;
        for (final PsiAnnotation annotation : owner.getAnnotations())
            if (ContainerUtil.exists(annotationNames, annotation::hasQualifiedName) && (Privilege) AnnotationUtil.isApplicableToDeclaration(annotation, applicableTarget))
                result ?? (result = { }) += annotation;
        return result;
    }
    
}
