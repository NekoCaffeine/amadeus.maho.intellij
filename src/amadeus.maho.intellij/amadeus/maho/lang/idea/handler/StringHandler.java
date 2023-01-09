package amadeus.maho.lang.idea.handler;

import java.util.IllegalFormatException;
import java.util.stream.Stream;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.impl.ConstantExpressionVisitor;
import com.intellij.psi.impl.IsConstantExpressionVisitor;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.ObjectHelper;

@TransformProvider
public class StringHandler {
    
    public static boolean isFormatted(final @Nullable PsiMethod method) = method?.getName()?.equals("formatted") ?? false && String.class.getCanonicalName().equals(method?.getContainingClass()?.getQualifiedName() ?? null);
    
    @Hook
    private static Hook.Result visitMethodCallExpression(final ConstantExpressionVisitor $this, final PsiMethodCallExpression expression) {
        if (isFormatted(expression.resolveMethod())) {
            final @Nullable PsiExpression qualifierExpression = expression.getMethodExpression().getQualifierExpression();
            if (qualifierExpression != null && (Privilege) $this.getStoredValue(qualifierExpression) instanceof String format) {
                final PsiExpression argExpressions[] = expression.getArgumentList().getExpressions();
                final Object args[] = Stream.of(argExpressions)
                        .map(argExpression -> (Privilege) $this.getStoredValue(argExpression))
                        .takeWhile(ObjectHelper::nonNull)
                        .toArray();
                if (args.length == argExpressions.length)
                    try {
                        (Privilege) ($this.myResult = format.formatted(args));
                        return Hook.Result.NULL;
                    } catch (final IllegalFormatException ignored) { }
            }
        }
        return Hook.Result.VOID;
    }
    
    @Hook(at = @At(field = @At.FieldInsn(name = "myIsConstant")), capture = true)
    private static boolean visitExpression(final boolean capture, final IsConstantExpressionVisitor $this, final PsiExpression expression)
            = capture || expression instanceof PsiMethodCallExpression callExpression && isFormatted(callExpression.resolveMethod());
    
}
