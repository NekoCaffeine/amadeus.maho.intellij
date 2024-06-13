package amadeus.maho.lang.idea.handler;

import java.util.IllegalFormatException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.intellij.codeInsight.daemon.impl.quickfix.ReplaceExpressionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.impl.ConstantExpressionVisitor;
import com.intellij.psi.impl.IsConstantExpressionVisitor;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.idea.handler.base.BaseSyntaxHandler;
import amadeus.maho.lang.idea.handler.base.Syntax;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.ObjectHelper;

import static amadeus.maho.lang.idea.handler.RegularExpressionHandler.PRIORITY;

@TransformProvider
@Syntax(priority = PRIORITY)
public class StringHandler extends BaseSyntaxHandler {
    
    public static final int PRIORITY = 1 << 24;
    
    static boolean isFormatted(final @Nullable PsiMethod method) = method?.getName()?.equals("formatted") ?? false && String.class.getCanonicalName().equals(method?.getContainingClass()?.getQualifiedName() ?? null);
    
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
    
    private static final Pattern CONVERSION_SPECIFIER_PATTERN = Pattern.compile("(?<!%)%s");
    
    @Override
    public void check(final PsiElement tree, final ProblemsHolder holder, final QuickFixFactory quickFix, final boolean isOnTheFly) {
        if (tree instanceof PsiMethodCallExpression methodCall && methodCall.getMethodExpression().getQualifierExpression() instanceof PsiLiteralExpression expression && isFormatted(methodCall.resolveMethod())) {
            final String string = expression.getText();
            final Matcher matcher = CONVERSION_SPECIFIER_PATTERN.matcher(string);
            final PsiExpressionList argumentList = methodCall.getArgumentList();
            if (matcher.results().count() == argumentList.getExpressionCount()) {
                final StringBuilder builder = { "STR." };
                final int p_offset[] = { 0 }, p_index[] = { 0 };
                final PsiExpression expressions[] = argumentList.getExpressions();
                matcher.reset().results().forEach(result -> {
                    builder.append(string, p_offset[0], result.start());
                    final PsiExpression arg = expressions[p_index[0]++];
                    if (arg instanceof PsiLiteralExpression literalExpression)
                        builder.append(literalExpression.getValue()?.toString() ?? "null");
                    else
                        builder.append('\\').append('{').append(arg.getText()).append('}');
                    p_offset[0] = result.end();
                });
                final String replaced = builder.append(string.substring(p_offset[0])).toString();
                final ReplaceExpressionAction fix = { methodCall, replaced, replaced };
                holder.registerProblem(methodCall, "`formatted` call can be replaced with template", ProblemHighlightType.WEAK_WARNING, LocalQuickFixAndIntentionActionOnPsiElement.from(fix, methodCall));
            }
        }
    }
    
}
