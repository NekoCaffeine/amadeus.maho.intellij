package amadeus.maho.lang.idea.handler;

import jdk.internal.ValueBased;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

import com.siyeh.ig.bugs.NumberEqualityInspection;

import static com.intellij.psi.JavaTokenType.EQEQ;

@TransformProvider
public class ValueBasedHandler {
    
    @Hook(at = @At(method = @At.MethodInsn(name = "getROperand")))
    private static Hook.Result visitBinaryExpression(final NumberEqualityInspection.NumberEqualityVisitor $this, final PsiBinaryExpression expression)
            = Hook.Result.falseToVoid(expression.getLOperand()?.getType() ?? null instanceof PsiClassType classType && isValueBasedClass(classType) && classType.equals(expression.getROperand()?.getType() ?? null), null);
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkPolyadicOperatorApplicable(final PsiPolyadicExpression expression) {
        if (expression.getOperationTokenType() == EQEQ) {
            final PsiExpression operands[] = expression.getOperands();
            if (operands.length > 1 && isValueBasedClass(operands[0].getType())) {
                {
                    final PsiType lType = TypeConversionUtil.erasure(operands[0].getType())!;
                    final @Nullable PsiType rType = TypeConversionUtil.erasure(operands[1].getType());
                    if (rType != null && !lType.equals(rType instanceof PsiPrimitiveType primitiveType ? primitiveType.getBoxedType(expression) : rType) && !PsiTypes.nullType().equals(rType))
                        return {
                                HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(
                                        STR."Comparison expressions of value types must be of the same type left and right.<br>left: \{lType}<br>right: \{rType}")
                        };
                }
                if (operands.length > 2) {
                    final PsiType lType = PsiTypes.booleanType();
                    for (int i = 2; i < operands.length; i++) {
                        final @Nullable PsiType rType = operands[i].getType();
                        if (!TypeConversionUtil.isBinaryOperatorApplicable(EQEQ, lType, rType, false) &&
                            !((Privilege) IncompleteModelUtil.isIncompleteModel(expression) &&
                              (Privilege) IncompleteModelUtil.isPotentiallyConvertible(lType, rType, expression))) {
                            final PsiJavaToken token = expression.getTokenBeforeOperand(operands[i])!;
                            return {
                                    HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(
                                            JavaErrorBundle.message("binary.operator.not.applicable", token.getText(), JavaHighlightUtil.formatType(lType), JavaHighlightUtil.formatType(rType)))
                            };
                        }
                    }
                }
                return Hook.Result.NULL;
            }
        }
        return Hook.Result.VOID;
    }
    
    public static boolean isValueBasedClass(final @Nullable PsiType type) {
        if (type == null)
            return false;
        final @Nullable PsiClass psiClass = PsiTypesUtil.getPsiClass(type);
        if (psiClass == null)
            return false;
        if (psiClass.hasAnnotation(ValueBased.class.getCanonicalName()))
            return true;
        return !(psiClass instanceof PsiExtensibleClass) && ContainerUtil.or(type.getSuperTypes(), ValueBasedHandler::isValueBasedClass);
    }
    
}
