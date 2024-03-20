package amadeus.maho.lang.idea.handler;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.formatter.java.JavaFormatterUtil;

import amadeus.maho.lang.idea.IDEAContext;
import amadeus.maho.lang.inspection.TypeModifier;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.DO_NOT_WRAP;

@TransformProvider
public class TypeModifierHandler {
    
    @Hook(value = JavaFormatterUtil.class, isStatic = true)
    private static Hook.Result getAnnotationWrapType(final ASTNode parent, final ASTNode child, final CommonCodeStyleSettings settings, final JavaCodeStyleSettings javaSettings)
            = Hook.Result.falseToVoid(child.getPsi() instanceof PsiAnnotation annotation && (AnnotationTargetUtil.isTypeAnnotation(annotation) || IDEAContext.marked(annotation, TypeModifier.class.getCanonicalName())), DO_NOT_WRAP);
    
}
