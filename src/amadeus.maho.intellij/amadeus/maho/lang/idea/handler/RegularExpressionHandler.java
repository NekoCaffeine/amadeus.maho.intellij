package amadeus.maho.lang.idea.handler;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;

import amadeus.maho.lang.idea.handler.base.BaseSyntaxHandler;
import amadeus.maho.lang.idea.handler.base.HandlerMarker;
import amadeus.maho.lang.idea.handler.base.Syntax;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.inspection.RegularExpression;
import amadeus.maho.util.tuple.Tuple2;

import static amadeus.maho.lang.idea.handler.RegularExpressionHandler.PRIORITY;

@Syntax(priority = PRIORITY)
public class RegularExpressionHandler extends BaseSyntaxHandler {
    
    public static final int PRIORITY = 1 << 24;
    
    @Override
    public void check(final PsiElement tree, final ProblemsHolder holder, final QuickFixFactory quickFix, final boolean isOnTheFly) {
        if (tree instanceof PsiExpression expression && expression.getType() != null && TypeConversionUtil.isAssignable(PsiType.getJavaLangString(tree.getManager(), tree.getResolveScope()), expression.getType()))
            switch (tree.getParent()) {
                case PsiVariable variable                                               -> {
                    if (variable.getInitializer() == expression)
                        check(expression, variable, holder, quickFix);
                }
                case PsiAssignmentExpression assignment when assignment.getLExpression() instanceof PsiReferenceExpression reference
                                                             && reference.resolve() instanceof PsiModifierListOwner owner -> {
                    if (assignment.getRExpression() == expression)
                        check(expression, owner, holder, quickFix);
                }
                case PsiNameValuePair pair                                              -> {
                    final @Nullable PsiMethod method = AnnotationUtil.getAnnotationMethod(pair);
                    if (method != null)
                        check(expression, method, holder, quickFix);
                }
                case PsiReturnStatement statement                                       -> {
                    final @Nullable PsiMethod method = PsiTreeUtil.getContextOfType(statement, PsiMethod.class);
                    if (method != null)
                        check(expression, method, holder, quickFix);
                }
                case null                                                               -> { }
                default                                                                 -> {
                    final @Nullable PsiVariable variable = DefaultValueHandler.defaultVariable(expression);
                    if (variable != null)
                        check(expression, variable, holder, quickFix);
                }
            }
    }
    
    public void check(final PsiExpression expression, final PsiModifierListOwner owner, final ProblemsHolder holder, final QuickFixFactory quickFix) {
        final List<Tuple2<RegularExpression, PsiAnnotation>> annotations = HandlerMarker.EntryPoint.getAnnotationsByType(owner, RegularExpression.class);
        if (annotations.size() == 1) {
            final Tuple2<RegularExpression, PsiAnnotation> tuple = annotations[0];
            check(expression, tuple.v1, tuple.v2, holder, quickFix);
        }
    }
    
    public void check(final @Nullable PsiExpression expression, final RegularExpression annotation, final PsiAnnotation annotationTree, final ProblemsHolder holder, final QuickFixFactory quickFix) {
        if (expression instanceof PsiConditionalExpression conditional) {
            check(conditional.getThenExpression(), annotation, annotationTree, holder, quickFix);
            check(conditional.getElseExpression(), annotation, annotationTree, holder, quickFix);
        } else if (expression != null) {
            final @Nullable String exceptionMessage = CachedValuesManager.getProjectPsiDependentCache(expression, it -> {
                final @Nullable Object result = JavaPsiFacade.getInstance(it.getProject()).getConstantEvaluationHelper().computeConstantExpression(it);
                if (result instanceof String regex)
                    try {
                        Pattern.compile(regex);
                    } catch (final PatternSyntaxException e) { return e.getMessage(); }
                return null;
            });
            if (exceptionMessage != null)
                holder.registerProblem(expression, "Invalid regular expression: %s".formatted(exceptionMessage));
        }
    }
    
}
