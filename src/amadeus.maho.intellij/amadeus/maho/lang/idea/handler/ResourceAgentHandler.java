package amadeus.maho.lang.idea.handler;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralValue;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.CachedValuesManager;

import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.ResourceAgent;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.inspection.Nullable;

@NoArgsConstructor
@Handler(ResourceAgent.class)
public class ResourceAgentHandler extends BaseHandler<ResourceAgent> {
    
    @Override
    public void check(final PsiElement tree, final ResourceAgent annotation, final PsiAnnotation annotationTree, final ProblemsHolder holder, final QuickFixFactory quickFix) {
        if (tree instanceof PsiMethod method) {
            if (!Stream.of(method.getParameterList().getParameters()).map(PsiParameter::getType).allMatch(type ->
                    type instanceof PsiClassType classType && String.class.getCanonicalName().equals(classType.resolve()?.getQualifiedName() ?? null)))
                holder.registerProblem(annotationTree, "Invalid parameter type, must be string type", ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
            final @Nullable Pattern pattern = pattern(annotationTree);
            if (pattern != null) {
                final Map<String, Integer> namedGroupsIndex = pattern.namedGroups();
                final List<String> missingKey = Stream.of(method.getParameterList().getParameters()).map(PsiParameter::getName).filterNot(namedGroupsIndex.keySet()::contains).toList();
                if (!missingKey.isEmpty())
                    holder.registerProblem(annotationTree, STR."The following group with the same name as the parameter declared in the method is missing from the resource agent's regular expression: \{missingKey}",
                            ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
            }
        }
    }
    
    public static Pattern pattern(final PsiAnnotation annotation) = CachedValuesManager.getProjectPsiDependentCache(annotation, it -> {
        try {
            return it.findAttributeValue(null) instanceof PsiLiteralValue value && value.getValue() instanceof String regex ? Pattern.compile(regex) : null;
        } catch (final PatternSyntaxException e) {
            return null;
        }
    });
    
}
