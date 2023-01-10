package amadeus.maho.lang.idea.handler;

import java.util.stream.Stream;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;

import amadeus.maho.lang.idea.handler.base.BaseSyntaxHandler;
import amadeus.maho.lang.idea.handler.base.Syntax;
import amadeus.maho.lang.inspection.Nullable;

import static amadeus.maho.lang.idea.handler.LetHandler.PRIORITY;

@Syntax(priority = PRIORITY)
public class LetHandler extends BaseSyntaxHandler {
    
    public static final int PRIORITY = 1 << 16;
    
    @Override
    public void check(final PsiElement tree, final ProblemsHolder holder, final QuickFixFactory quickFix, final boolean isOnTheFly) {
        if (tree instanceof PsiMethodCallExpression callExpression && callExpression.resolveMethod() instanceof ExtensionHandler.ExtensionMethod extensionMethod && "let".equals(extensionMethod.name())) {
            final @Nullable PsiExpression qualifierExpression = callExpression.getMethodExpression().getQualifierExpression();
            if (qualifierExpression != null && qualifierExpression.getType() instanceof PsiClassType classType) {
                final @Nullable PsiClass resolved = classType.resolve();
                if (resolved != null && Stream.class.getCanonicalName().equals(resolved.getQualifiedName()))
                    holder.registerProblem(callExpression, "Since the operation on the stream will return a new identity, this call is suspicious. An IllegalStateException will be thrown.", ProblemHighlightType.WARNING);
            }
        }
    }
    
}
