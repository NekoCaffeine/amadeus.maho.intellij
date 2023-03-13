package amadeus.maho.lang.idea.handler;

import jdk.internal.ValueBased;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.containers.ContainerUtil;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

import com.siyeh.ig.bugs.NumberEqualityInspection;

@TransformProvider
public class ValueBasedHandler {
    
    @Hook(at = @At(method = @At.MethodInsn(name = "getROperand")))
    private static Hook.Result visitBinaryExpression(final NumberEqualityInspection.NumberEqualityVisitor $this, final PsiBinaryExpression expression)
            = Hook.Result.falseToVoid(expression.getLOperand()?.getType() ?? null instanceof PsiClassType classType && isValueBasedClass(classType) && classType.equals(expression.getROperand()?.getType() ?? null), null);
    
    public static boolean isValueBasedClass(final PsiType type) {
        final @Nullable PsiClass psiClass = PsiTypesUtil.getPsiClass(type);
        if (psiClass == null)
            return false;
        if (psiClass.hasAnnotation(ValueBased.class.getCanonicalName()))
            return true;
        return ContainerUtil.or(type.getSuperTypes(), ValueBasedHandler::isValueBasedClass);
    }
    
}
