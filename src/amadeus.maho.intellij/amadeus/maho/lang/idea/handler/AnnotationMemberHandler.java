package amadeus.maho.lang.idea.handler;

import java.lang.annotation.Annotation;
import java.util.stream.Stream;

import com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class AnnotationMemberHandler {
    
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
    
}
