package amadeus.maho.lang.idea.handler;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.java.JavaDfaValueFactory;
import com.intellij.codeInspection.dataFlow.java.inst.AssignInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.DupInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.PopInstruction;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.BasicOldExpressionParser;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiUnaryExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.tree.java.PsiPrefixExpressionImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;

import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.idea.handler.base.BaseSyntaxHandler;
import amadeus.maho.lang.idea.handler.base.Syntax;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.MethodDescriptor;
import amadeus.maho.transform.mark.base.TransformProvider;

import static amadeus.maho.lang.idea.handler.AddressOfHandler.PRIORITY;
import static amadeus.maho.util.bytecode.Bytecodes.NEW;
import static com.intellij.psi.JavaTokenType.*;
import static com.intellij.psi.PsiTypes.*;

@TransformProvider
@Syntax(priority = PRIORITY)
public class AddressOfHandler extends BaseSyntaxHandler {
    
    public static final int PRIORITY = 1 << 12;
    
    @ToString
    @EqualsAndHashCode
    public record ArgInfo(PsiExpression arg, Set<IElementType> tags) { }
    
    @Override
    public void check(final PsiElement tree, final ProblemsHolder holder, final QuickFixFactory quickFix, final boolean isOnTheFly) {
        if (tree instanceof PsiPrefixExpression expression && expression.getOperationSign().getTokenType() == AND) {
            final ArgInfo argInfo = argInfo(expression);
            if (!argInfo.tags().contains(PLUS) && argInfo.arg() instanceof PsiMethodCallExpression callExpression)
                holder.registerProblem(callExpression, "Unable to write value from off-heap memory back to method call expression", ProblemHighlightType.GENERIC_ERROR);
        }
    }
    
    @Hook(at = @At(method = @At.MethodInsn(name = "contains", descriptor = @MethodDescriptor(value = boolean.class, parameters = IElementType.class))), before = false, capture = true)
    private static boolean parseUnary(final boolean capture, final BasicOldExpressionParser $this, final PsiBuilder builder, final int mode) = capture || builder.getTokenType() == AND;
    
    @Hook
    private static Hook.Result getType(final PsiPrefixExpressionImpl $this) = Hook.Result.falseToVoid($this.getOperationSign().getTokenType() == AND, longType());
    
    private static final Set<String> addressOfApplicableType = Stream.of(byteType(), shortType(), intType(), longType(), floatType(), doubleType()).map(type -> type.getCanonicalText(false)).collect(Collectors.toSet());
    
    @Hook(value = TypeConversionUtil.class, isStatic = true)
    private static Hook.Result isUnaryOperatorApplicable(final PsiJavaToken token, final PsiType type)
            = token.getTokenType() == AND ? type instanceof PsiPrimitiveType && addressOfApplicableType.contains(type.getCanonicalText(false)) ? Hook.Result.TRUE : Hook.Result.FALSE : Hook.Result.VOID;
    
    private static ArgInfo argInfo(final PsiUnaryExpression expression) {
        PsiExpression arg = PsiUtil.skipParenthesizedExprDown(expression);
        if (!(expression.getOperand() instanceof PsiUnaryExpression))
            return { arg, Set.of() };
        final HashSet<IElementType> tags = { };
        while (arg instanceof PsiUnaryExpression unary) {
            tags += unary.getOperationTokenType();
            arg = PsiUtil.skipParenthesizedExprDown(unary.getOperand());
        }
        return { arg, tags };
    }
    
    @Hook(at = @At(type = @At.TypeInsn(opcode = NEW, type = PopInstruction.class), offset = -1), jump = @At(method = @At.MethodInsn(name = "finishElement"), offset = -2))
    private static Hook.Result visitPrefixExpression(final ControlFlowAnalyzer $this, final PsiPrefixExpression expression) {
        if (expression.getOperationSign().getTokenType() == AND) {
            final ArgInfo argInfo = argInfo(expression);
            final PsiExpression operand = argInfo.arg();
            (Privilege) $this.addInstruction(new DupInstruction());
            (Privilege) $this.addInstruction(new AssignInstruction(operand, null, JavaDfaValueFactory.getExpressionDfaValue((Privilege) $this.myFactory, operand)));
            return new Hook.Result().jump();
        }
        return Hook.Result.VOID;
    }
    
    @Hook(at = @At(method = @At.MethodInsn(name = "finishElement")))
    private static void visitPrefixExpression(final com.intellij.psi.controlFlow.ControlFlowAnalyzer $this, final PsiPrefixExpression expression) {
        if (expression.getOperationSign().getTokenType() == AND) {
            final ArgInfo argInfo = argInfo(expression);
            final PsiExpression operand = argInfo.arg();
            if (operand instanceof PsiReferenceExpression referenceExpression) {
                final @Nullable PsiVariable variable = (Privilege) $this.getUsedVariable(referenceExpression);
                if (variable != null) {
                    if (!argInfo.tags().contains(MINUS))
                        (Privilege) $this.generateReadInstruction(variable);
                    if (!argInfo.tags().contains(PLUS))
                        (Privilege) $this.generateWriteInstruction(variable);
                }
            }
        }
    }
    
    private static @Nullable PsiUnaryExpression unary(final PsiExpression expression) {
        @Nullable PsiUnaryExpression unary = expression.getParent() instanceof PsiUnaryExpression parent ? parent : null;
        while (unary != null && unary.getParent() instanceof PsiUnaryExpression parent)
            unary = parent;
        return unary;
    }
    
    @Hook(value = PsiUtil.class, isStatic = true)
    private static Hook.Result isAccessedForReading(final PsiExpression expression) {
        final @Nullable PsiUnaryExpression unary = unary(expression);
        if (unary != null && unary.getOperationTokenType() == AND) {
            final ArgInfo argInfo = argInfo(unary);
            return { !argInfo.tags().contains(MINUS) };
        }
        return Hook.Result.VOID;
    }
    
    @Hook(value = PsiUtil.class, isStatic = true)
    private static Hook.Result isAccessedForWriting(final PsiExpression expression) {
        final @Nullable PsiUnaryExpression unary = unary(expression);
        if (unary != null && unary.getOperationTokenType() == AND) {
            final ArgInfo argInfo = argInfo(unary);
            return { !argInfo.tags().contains(PLUS) };
        }
        return Hook.Result.VOID;
    }
    
}
