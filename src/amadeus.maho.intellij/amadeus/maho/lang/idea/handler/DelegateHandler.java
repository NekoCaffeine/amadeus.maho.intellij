package amadeus.maho.lang.idea.handler;

import java.util.List;
import java.util.stream.Stream;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;

import amadeus.maho.lang.Delegate;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.ExtensibleMembers;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.light.LightBridgeMethod;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.base.TransformProvider;

import static amadeus.maho.lang.idea.handler.DelegateHandler.PRIORITY;

@Handler(value = Delegate.class, priority = PRIORITY)
@TransformProvider
public class DelegateHandler extends BaseHandler<Delegate> {
    
    public static final int PRIORITY = ConstructorHandler.PRIORITY << 2;
    
    @Override
    public boolean contextFilter(final PsiClass context) = true;
    
    private static Stream<? extends PsiClassType> delegateTypes(final Delegate annotation, final PsiClassType classType) {
        final List<? extends PsiClassType> only = annotation.accessPsiClasses(Delegate::only).toList();
        return only.isEmpty() ? Stream.of(classType) : only.stream();
    }
    
    private static void process(final @Nullable PsiType type, final PsiClass context, final PsiMember member, final Delegate annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members) {
        if (type instanceof PsiClassType classType && !(member instanceof LightBridgeMethod))
            delegateTypes(annotation, classType).forEach(psiType -> {
                final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(psiType);
                final PsiClass psiClass = resolveResult.getElement();
                if (psiClass != null) {
                    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
                    Stream.of(psiClass.getAllMethods())
                            .filter(method -> !method.isConstructor() && method.hasModifierProperty(PsiModifier.PUBLIC) && !method.hasModifierProperty(PsiModifier.STATIC))
                            .filter(method -> !(method.getContainingClass()?.getQualifiedName()?.equals(Object.class.getName()) ?? true))
                            .forEach(method -> {
                                final LightBridgeMethod wrapper = { context, method, substitutor, annotationTree };
                                wrapper.virtual(true);
                                if (members.shouldInject(wrapper)) {
                                    wrapper.setNavigationElement(method);
                                    wrapper.getModifierList().modifiers().remove(PsiModifier.ABSTRACT);
                                    if (member.hasModifierProperty(PsiModifier.STATIC))
                                        wrapper.getModifierList().addModifier(PsiModifier.STATIC);
                                    wrapper.setMethodKind(Delegate.class.getCanonicalName());
                                    members.inject(wrapper);
                                }
                            });
                }
            });
    }
    
    @Override
    public void processVariable(final PsiField tree, final Delegate annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context)
            = process(tree.getType(), context, tree, annotation, annotationTree, members);
    
    @Override
    public void processMethod(final PsiMethod tree, final Delegate annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) {
        if (tree.getParameterList().getParametersCount() == 0)
            process(tree.getReturnType(), context, tree, annotation, annotationTree, members);
    }
    
    @Override
    public void check(final PsiElement tree, final Delegate annotation, final PsiAnnotation annotationTree, final ProblemsHolder holder, final QuickFixFactory quickFix) {
        if (tree instanceof PsiMethod method && method.getParameterList().getParametersCount() != 0)
            holder.registerProblem(annotationTree, "The delegate method must have no parameters.", ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
    }
    
}
