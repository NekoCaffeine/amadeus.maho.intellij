package amadeus.maho.lang.idea.handler;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.findUsages.JavaFindUsagesHelper;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.ExtensibleMembers;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.light.LightBridgeMethod;
import amadeus.maho.lang.idea.light.LightMethod;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;

import static amadeus.maho.lang.idea.IDEAContext.OperatorData.operatorSymbol2operatorName;
import static amadeus.maho.lang.idea.handler.SetterHandler.PRIORITY;

@Handler(value = Extension.Operator.class, priority = PRIORITY)
public class ExtensionOperatorHandler extends BaseHandler<Extension.Operator> {
    
    public static final int PRIORITY = ReferenceHandler.PRIORITY >> 2;
    
    @NoArgsConstructor
    public static class DerivedMethod extends LightMethod { }
    
    @Override
    public void processMethod(final PsiMethod tree, final Extension.Operator annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) {
        if (tree instanceof LightBridgeMethod)
            return;
        final DerivedMethod methodTree = { context, tree, PsiSubstitutor.EMPTY, annotationTree, tree };
        methodTree.name(operatorSymbol2operatorName.getOrDefault(annotation.value(), annotation.value()));
        if (members.shouldInject(methodTree)) {
            Stream.of(PsiModifier.ABSTRACT, PsiModifier.NATIVE, PsiModifier.SYNCHRONIZED).forEach(methodTree.getModifierList().modifiers()::remove);
            if (context.isInterface())
                methodTree.getModifierList().modifiers().add(PsiModifier.DEFAULT);
            members.inject(methodTree);
        }
    }
    
    @Override
    public void collectRelatedTarget(final PsiModifierListOwner tree, final Extension.Operator annotation, final PsiAnnotation annotationTree, final Set<PsiNameIdentifierOwner> targets) = derivedMethods(tree).forEach(targets::add);
    
    private static Stream<? extends PsiMethod> derivedMethods(final PsiModifierListOwner tree) = tree instanceof PsiMethod method ?
            Stream.of(method.getContainingClass().getAllMethods())
                    .cast(DerivedMethod.class)
                    .filter(derived -> derived.source() == method) : Stream.empty();
    
    @Hook(value = JavaFindUsagesHelper.class, isStatic = true)
    private static Hook.Result processElementUsages(final PsiElement element, final FindUsagesOptions options, final Processor<? super UsageInfo> processor) {
        final @Nullable PsiMethod target = element instanceof DerivedMethod derivedMethod ? derivedMethod.source() : element instanceof PsiMethod method ? method : null;
        if (target != null) {
            final List<? extends PsiMethod> methods = derivedMethods(target).toList();
            if (methods.nonEmpty())
                return Stream.concat(Stream.of(target), methods.stream()).allMatch(method -> JavaFindUsagesHelper.processElementUsages(method, options, processor)) ? Hook.Result.TRUE : Hook.Result.FALSE;
        }
        return Hook.Result.VOID;
    }
    
}
