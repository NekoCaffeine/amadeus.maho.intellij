package amadeus.maho.lang.idea.handler;

import java.util.IllegalFormatException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMethodUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.ReplaceExpressionAction;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiTemplateExpression;
import com.intellij.psi.impl.ConstantExpressionVisitor;
import com.intellij.psi.impl.IsConstantExpressionVisitor;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.ObjectHelper;

@TransformProvider
public interface StringHandler {
    
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
    
    @Hook(value = HighlightUtil.class, isStatic = true, at = @At(method = @At.MethodInsn(name = "descriptionAndTooltip"), ordinal = 0), before = false, capture = true)
    private static void checkTemplateExpression(final HighlightInfo.Builder capture, final PsiTemplateExpression expression) {
        final String string = expression.getTemplate().getText(), replaced = STR."STR.\{string}";
        final ReplaceExpressionAction fix = { expression, replaced, replaced };
        capture.registerFix(fix, null, null, null, null);
    }
    
    Pattern CONVERSION_SPECIFIER_PATTERN = Pattern.compile("(?<!%)%s");
    
    @Hook(value = HighlightMethodUtil.class, isStatic = true)
    private static void checkMethodCall(final PsiMethodCallExpression methodCall, final PsiResolveHelper resolveHelper, final LanguageLevel languageLevel,
            final JavaSdkVersion javaSdkVersion, final PsiFile file, final Consumer<? super HighlightInfo.Builder> errorSink) {
        if (methodCall.getMethodExpression().getQualifierExpression() instanceof PsiLiteralExpression expression && expression.getValue() instanceof String string && isFormatted(methodCall.resolveMethod())) {
            final Matcher matcher = CONVERSION_SPECIFIER_PATTERN.matcher(string);
            final PsiExpressionList argumentList = methodCall.getArgumentList();
            if (matcher.results().count() == argumentList.getExpressionCount()) {
                final StringBuilder builder = { "STR." };
                final String sign = expression.isTextBlock() ? "\"\"\"" : "\"";
                builder.append(sign);
                final int p_offset[] = { 0 }, p_index[] = { 0 };
                final PsiExpression expressions[] = argumentList.getExpressions();
                matcher.reset().results().forEach(result -> {
                    builder.append(string, p_offset[0], result.start());
                    builder.append('\\').append('{').append(expressions[p_index[0]++].getText()).append('}');
                    p_offset[0] = result.end();
                });
                final String replaced = builder.append(sign).toString();
                final ReplaceExpressionAction fix = { methodCall, replaced, replaced };
                errorSink.accept(HighlightInfo.newHighlightInfo(HighlightInfoType.WEAK_WARNING)
                        .range(methodCall)
                        .descriptionAndTooltip("`formatted` call can be replaced with template")
                        .registerFix(fix, null, null, null, null));
            }
        }
    }
    
}
