package amadeus.maho.lang.idea.handler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;

import amadeus.maho.lang.Delegate;
import amadeus.maho.lang.idea.IDEAContext;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.ClassDeclarationsProcessor;
import amadeus.maho.lang.idea.handler.base.ExtensibleMembers;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.handler.base.ImplicitUsageChecker;
import amadeus.maho.lang.idea.light.LightBridgeElement;
import amadeus.maho.lang.idea.light.LightBridgeField;
import amadeus.maho.lang.idea.light.LightBridgeMethod;
import amadeus.maho.lang.idea.light.LightMethod;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;

import static amadeus.maho.lang.idea.handler.DelegateHandler.PRIORITY;

@Handler(value = Delegate.class, priority = PRIORITY)
@TransformProvider
public class DelegateHandler extends BaseHandler<Delegate> {
    
    public static final int PRIORITY = ConstructorHandler.PRIORITY << 2;
    
    private static final Set<String> objectMethodIdentities = Set.of("getClass()", "equals(java.lang.Object)", "hashCode()", "toString()", "clone()", "notify()", "notifyAll()", "wait()", "wait(long)", "wait(long,int)");
    
    @Override
    public boolean contextFilter(final PsiClass context) = true;
    
    private static Stream<? extends PsiClassType> delegateTypes(final Delegate annotation, final PsiClassType classType) {
        final List<? extends PsiClassType> only = annotation.accessPsiClasses(Delegate::only).toList();
        return only.isEmpty() ? Stream.of(classType) : only.stream();
    }
    
    private static void process(final @Nullable PsiType type, final PsiClass context, final PsiMember member, final Delegate annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members) {
        if (type instanceof PsiClassType classType && !(member instanceof LightBridgeElement)) {
            final boolean staticSource = member.hasModifierProperty(PsiModifier.STATIC);
            if (annotation.hard()) {
                delegateTypes(annotation, classType).forEach(psiType -> {
                    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(psiType);
                    final @Nullable PsiClass psiClass = resolveResult.getElement();
                    if (psiClass != null) {
                        final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
                        Stream.of(psiClass.getAllMethods())
                                .filter(method -> !method.isConstructor() && method.hasModifierProperty(PsiModifier.PUBLIC) && !method.hasModifierProperty(PsiModifier.STATIC))
                                .filter(method -> !objectMethodIdentities.contains(IDEAContext.methodIdentity(method)))
                                .forEach(method -> {
                                    final LightMethod wrapper = { context, method, substitutor };
                                    if (members.shouldInject(wrapper)) {
                                        wrapper.setContainingClass(context);
                                        wrapper.setNavigationElement(method);
                                        wrapper.getModifierList().modifiers().remove(PsiModifier.ABSTRACT);
                                        if (staticSource)
                                            wrapper.getModifierList().addModifier(PsiModifier.STATIC);
                                        wrapper.setMethodKind(Delegate.class.getCanonicalName());
                                        members.inject(wrapper);
                                    }
                                });
                    }
                });
            } else {
                final List<? extends PsiClassType> delegateTypes = delegateTypes(annotation, classType).toList();
                members.injectBridgeProvider((pdc, staticOnly) -> {
                    if ((!staticOnly || staticSource) && pdc.type() instanceof PsiClassType contextType && contextType.resolve() instanceof PsiClass derived)
                        return CachedValuesManager.getProjectPsiDependentCache(derived, _ ->
                                        new ConcurrentWeakIdentityHashMap<PsiAnnotation, ConcurrentHashMap<PsiClassType, List<? extends LightBridgeElement>>>())
                                .computeIfAbsent(annotationTree, _ -> new ConcurrentHashMap<>())
                                .computeIfAbsent(contextType, _ -> delegateTypes.stream()
                                        .map(substitutor(pdc, context, derived)::substitute)
                                        .cast(PsiClassType.class)
                                        .flatMap(delegateType -> {
                                            final PsiClassType.ClassResolveResult delegateResult = delegateType.resolveGenerics();
                                            final @Nullable PsiClass delegateClass = delegateResult.getElement();
                                            final HashSet<String> record = { };
                                            if (delegateClass != null) {
                                                return Stream.<PsiMember>concat(Stream.of(delegateClass.getAllFields()), Stream.of(delegateClass.getAllMethods()))
                                                        .filter(it -> it.hasModifierProperty(PsiModifier.PUBLIC) && !it.hasModifierProperty(PsiModifier.STATIC))
                                                        .filter(it -> !(it instanceof PsiMethod method) || !method.isConstructor() && !objectMethodIdentities[IDEAContext.methodIdentity(method)])
                                                        .filter(it -> record.add(switch (it) {
                                                            case PsiField field   -> field.getName();
                                                            case PsiMethod method -> IDEAContext.methodIdentity(method);
                                                            default               -> null;
                                                        }))
                                                        .map(it -> (LightBridgeElement) switch (it) {
                                                            case PsiField field   -> {
                                                                final LightBridgeField wrapper = { context, field.getName(), delegateType.resolveGenerics().getSubstitutor().substitute(field.getType()), annotationTree, field };
                                                                wrapper.setContainingClass(context);
                                                                wrapper.setNavigationElement(field);
                                                                if (staticSource)
                                                                    wrapper.getModifierList().addModifier(PsiModifier.STATIC);
                                                                yield wrapper;
                                                            }
                                                            case PsiMethod method -> {
                                                                final LightBridgeMethod wrapper = { context, method, delegateType.resolveGenerics().getSubstitutor(), annotationTree, method };
                                                                wrapper.setContainingClass(derived);
                                                                wrapper.setNavigationElement(method);
                                                                if (staticSource)
                                                                    wrapper.getModifierList().addModifier(PsiModifier.STATIC);
                                                                wrapper.setMethodKind(Delegate.class.getCanonicalName());
                                                                yield wrapper;
                                                            }
                                                            default               -> null;
                                                        })
                                                        .nonnull();
                                            }
                                            return Stream.empty();
                                        })
                                        .toList());
                    return List.of();
                });
            }
        }
    }
    
    private static PsiSubstitutor substitutor(final ClassDeclarationsProcessor.ProcessDeclarationsContext pdc, final PsiClass context, final PsiClass derived)
            = pdc.qualifier() ? pdc.substitutor() : TypeConversionUtil.getClassSubstitutor(context, derived, pdc.substitutor()) ?? pdc.substitutor();
    
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
        if (tree instanceof PsiModifierListOwner owner && owner.hasModifierProperty(PsiModifier.STATIC) && annotation.hard())
            holder.registerProblem(annotationTree, "The target of a hard delegate must be a non-static member", ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
        if (tree instanceof PsiMethod method && method.getParameterList().getParametersCount() != 0)
            holder.registerProblem(annotationTree, "The delegate method must have no parameters", ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
        final @Nullable PsiType type = switch (tree) {
            case PsiField field   -> field.getType();
            case PsiMethod method -> method.getReturnType();
            default               -> null;
        };
        if (type instanceof PsiPrimitiveType)
            holder.registerProblem(annotationTree, "The delegate type cannot be a primitive type", ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
        if (type instanceof PsiArrayType)
            holder.registerProblem(annotationTree, "The delegate type cannot be an array type", ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
    }
    
    @Override
    public boolean isImplicitUsage(final PsiElement tree, final ImplicitUsageChecker.RefData refData)
            = tree instanceof PsiModifierListOwner owner && !(tree instanceof PsiLocalVariable) && owner.hasAnnotation(handler().value().getCanonicalName());
    
}
