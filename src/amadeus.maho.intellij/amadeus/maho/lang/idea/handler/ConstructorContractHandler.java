package amadeus.maho.lang.idea.handler;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.impl.source.PsiExtensibleClass;

import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.inspection.ConstructorContract;
import amadeus.maho.lang.inspection.Nullable;

@Handler(ConstructorContract.class)
public class ConstructorContractHandler extends BaseHandler<ConstructorContract> {
    
    @Override
    public void check(final PsiElement tree, final ConstructorContract annotation, final PsiAnnotation annotationTree, final ProblemsHolder holder, final QuickFixFactory quickFix) {
        if (tree instanceof PsiExtensibleClass extensibleClass && annotation.value().length > 0) {
            final @Nullable PsiIdentifier identifier = extensibleClass.getNameIdentifier();
            if (identifier != null) {
                final Predicate<ConstructorContract.Parameters> predicate = parameters -> hasConstructor(extensibleClass, accessPsiClasses(parameters, ConstructorContract.Parameters::value));
                final Stream<ConstructorContract.Parameters> stream = Stream.of(annotation.value());
                if (!(annotation.anyMatch() ? stream.anyMatch(predicate) : stream.allMatch(predicate)))
                    holder.registerProblem(identifier, STR."Constraints of the constructor contract are not met: \{annotationTree.getText()}", ProblemHighlightType.GENERIC_ERROR,
                            ConstructorHandler.constructorHandlers.stream()
                                    .map(BaseHandler::handler).map(Handler::value)
                                    .map(annotationType -> new AddAnnotationPsiFix(annotationType.getCanonicalName(), extensibleClass))
                                    .toArray(LocalQuickFix[]::new));
            }
        }
    }
    
    public boolean hasConstructor(final PsiExtensibleClass extensibleClass, final List<? extends PsiClassType> classTypes)
            = Stream.of(extensibleClass.getConstructors()).anyMatch(constructor -> Stream.of(constructor.getParameterList().getParameters()).map(PsiParameter::getType).toList().equals(classTypes));
    
}
