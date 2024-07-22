package amadeus.maho.lang.idea.handler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.impl.light.LightTypeParameterListBuilder;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.idea.IDEAContext;
import amadeus.maho.lang.idea.handler.base.BaseSyntaxHandler;
import amadeus.maho.lang.idea.handler.base.HandlerSupport;
import amadeus.maho.lang.idea.handler.base.ImplicitUsageChecker;
import amadeus.maho.lang.idea.handler.base.Syntax;
import amadeus.maho.lang.idea.light.LightMethod;
import amadeus.maho.lang.idea.light.LightParameter;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.function.FunctionHelper;

import static amadeus.maho.lang.idea.handler.ExtensionHandler.PRIORITY;

@TransformProvider
@Syntax(priority = PRIORITY)
public class ExtensionHandler extends BaseSyntaxHandler {
    
    public static final int PRIORITY = 1 << 2;
    
    @TransformProvider
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class ExtensionMethod extends LightMethod {
        
        PsiMethod sourceMethod;
        
        public ExtensionMethod(final PsiManager manager, final String name, final PsiMethod sourceMethod) {
            super(manager, name);
            this.sourceMethod = sourceMethod;
            mark("@Extension");
        }
        
        @Override
        public boolean isEquivalentTo(final PsiElement another) = super.isEquivalentTo(another) || sourceMethod().equals(another);
        
        @Override
        public @Nullable ExtensionMethod copy() = null;
        
        // When the first parameter is annotated with @Nullable, the call that may be null is not checked.
        @Hook
        private static <T extends PsiElement> Hook.Result ifMyProblem(final NullabilityProblemKind<T> $this, final NullabilityProblemKind.NullabilityProblem<?> problem, final Consumer<? super T> consumer) {
            if ($this == NullabilityProblemKind.callNPE) {
                final NullabilityProblemKind.NullabilityProblem<T> myProblem = $this.asMyProblem(problem);
                if (myProblem != null && skipNullabilityCheck(((PsiMethodCallExpression) myProblem.getAnchor()).resolveMethod()))
                    return Hook.Result.NULL;
            } else if ($this == NullabilityProblemKind.callMethodRefNPE) {
                final NullabilityProblemKind.NullabilityProblem<T> myProblem = $this.asMyProblem(problem);
                if (myProblem != null) {
                    final PsiElement element = ((PsiMethodReferenceExpression) myProblem.getAnchor()).getReferenceNameElement();
                    if (element instanceof PsiMethod method && skipNullabilityCheck(method))
                        return Hook.Result.NULL;
                }
            }
            return Hook.Result.VOID;
        }
        
        public static boolean skipNullabilityCheck(final @Nullable PsiMethod method)
                = method instanceof ExtensionMethod extensionMethod && extensionMethod.sourceMethod().getParameterList().getParameters()[0].hasAnnotation(Nullable.class.getCanonicalName());
        
        @Hook
        private static Hook.Result isRawSubstitution(final MethodCandidateInfo $this) = Hook.Result.falseToVoid($this.getElement() instanceof ExtensionMethod, false);
        
        @Hook
        private static Hook.Result visitMethodCallExpression(final EvaluatorBuilderImpl.Builder $this, @Hook.Reference PsiMethodCallExpression expression) {
            if (expression.resolveMethod() instanceof ExtensionMethod extensionMethod && extensionMethod.sourceMethod().getContainingClass() instanceof PsiExtensibleClass containing) {
                final PsiMethod sourceMethod = extensionMethod.sourceMethod();
                final String args = Stream.concat(Stream.of(expression.getMethodExpression().getQualifierExpression()?.getText() ?? "this"),
                        Stream.of(expression.getArgumentList().getExpressions()).map(PsiElement::getText)).collect(Collectors.joining(", "));
                expression = (PsiMethodCallExpression) JavaPsiFacade.getElementFactory(expression.getProject())
                        .createExpressionFromText(STR."\{containing.getQualifiedName()}.\{sourceMethod.getName()}(\{args})", expression);
                return { };
            }
            return Hook.Result.VOID;
        }
        
    }
    
    @ToString
    @EqualsAndHashCode
    public record ExtensionMethodCacheByType(Predicate<PsiType> predicate, BiFunction<PsiClass, PsiType, ExtensionMethod> provider) { }
    
    public static boolean isProvider(final @Nullable PsiClass node) = node != null && node.hasAnnotation(Extension.class.getCanonicalName()) && node.hasModifierProperty(PsiModifier.PUBLIC);
    
    public static boolean isExtensionMethodOrSource(final @Nullable PsiElement element) = isExtensionMethod(element) || isExtensionMethodSource(element);
    
    public static boolean isExtensionMethod(final @Nullable PsiElement element) = element instanceof ExtensionMethod;
    
    public static boolean isExtensionMethodSource(final @Nullable PsiElement element) = element instanceof PsiMethod && isProvider(PsiTreeUtil.getContextOfType(element, PsiClass.class));
    
    public static PsiElement getSource(final PsiElement element) = element instanceof ExtensionMethod extensionMethod ? extensionMethod.sourceMethod() : element;
    
    public static Collection<ExtensionMethod> memberCache(final GlobalSearchScope resolveScope, final PsiClass psiClass, final PsiType type, final List<PsiClass> supers, final PsiSubstitutor substitutor) {
        final @Nullable Project project = resolveScope.getProject();
        if (project == null)
            return List.of();
        final List<ExtensionMethodCacheByType> caches = CachedValuesManager.getManager(project).getCachedValue(project, () -> CachedValueProvider.Result.create(
                        new ConcurrentHashMap<GlobalSearchScope, List<ExtensionMethodCacheByType>>(), PsiModificationTracker.getInstance(project)))
                .computeIfAbsent(resolveScope, scope -> extensionSet(scope).stream()
                        .map(ExtensionHandler::providerData)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList()));
        return CachedValuesManager.getProjectPsiDependentCache(psiClass, it -> new ConcurrentHashMap<GlobalSearchScope, Map<String, Supplier<Collection<ExtensionMethod>>>>())
                .computeIfAbsent(resolveScope, _ -> new ConcurrentHashMap<>()).computeIfAbsent(type.getCanonicalText(), it -> FunctionHelper.lazy(() -> supers.stream().flatMap(node -> {
                    final PsiType contextType = psiClass == node ? type : new PsiImmediateClassType(node, TypeConversionUtil.getSuperClassSubstitutor(node, psiClass, substitutor));
                    return caches.stream()
                            .filter(cache -> cache.predicate().test(contextType))
                            .map(cache -> cache.provider().apply(psiClass, contextType));
                }).collect(Collectors.toList()))).get();
    }
    
    public static boolean checkMethod(final Collection<PsiMethod> members, final Map<PsiClass, Collection<PsiMethod>> record, final PsiMethod methodTree) {
        final PsiElement navigation = methodTree.getNavigationElement();
        final PsiMethod method = navigation == methodTree ? methodTree : navigation instanceof PsiMethod navigationMethod ? navigationMethod : methodTree;
        final Collection<PsiMethod> collection = record.computeIfAbsent(method.getContainingClass(), FunctionHelper.abandon(ArrayList::new));
        try {
            return Stream.concat(members.stream(), collection.stream())
                    .map(tree -> MethodSignatureBackedByPsiMethod.create(tree, PsiSubstitutor.EMPTY, true))
                    .noneMatch(MethodSignatureBackedByPsiMethod.create(methodTree, PsiSubstitutor.EMPTY, true)::equals);
        } finally { collection.add(methodTree); }
    }
    
    private static final String canonicalName = Extension.class.getCanonicalName(), simpleName = Extension.class.getSimpleName();
    
    public static Collection<PsiClass> extensionSet(final GlobalSearchScope resolveScope) {
        final @Nullable Project project = resolveScope.getProject();
        if (project == null)
            return List.of();
        return CachedValuesManager.getManager(project).getCachedValue(project, () -> CachedValueProvider.Result.create(new ConcurrentHashMap<GlobalSearchScope, Collection<PsiClass>>(), PsiModificationTracker.getInstance(project)))
                .computeIfAbsent(resolveScope, ExtensionHandler::syncExtensionSet);
    }
    
    private static Collection<PsiClass> syncExtensionSet(final GlobalSearchScope resolveScope) {
        final @Nullable Project project = resolveScope.getProject();
        if (project == null)
            return List.of();
        return JavaAnnotationIndex.getInstance().getAnnotations(Extension.class.getSimpleName(), project, resolveScope).stream()
                .filter(annotation -> HandlerSupport.checkAnnotationType(canonicalName, simpleName, annotation))
                .map(PsiElement::getParent)
                .map(PsiElement::getParent)
                .cast(PsiClass.class)
                .collect(Collectors.toList());
    }
    
    public static List<ExtensionMethodCacheByType> providerData(final PsiClass node) = CachedValuesManager.getProjectPsiDependentCache(node, ExtensionHandler::syncProviderData);
    
    private static List<ExtensionMethodCacheByType> syncProviderData(final PsiClass node) {
        // Improve code completion speed with lazy loading and built-in caching.
        final List<ExtensionMethodCacheByType> result = new ArrayList<>();
        final Project project = node.getProject();
        final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(project).getResolveHelper();
        final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(project);
        Stream.of(node.getMethods())
                .filter(methodNode -> methodNode.hasModifierProperty(PsiModifier.PUBLIC) && methodNode.hasModifierProperty(PsiModifier.STATIC))
                .filter(methodNode -> methodNode.getParameterList().getParametersCount() > 0)
                // Cannot be applied to the primitive type.
                .filter(methodNode -> !(methodNode.getParameterList().getParameters()[0].getType() instanceof PsiPrimitiveType))
                .filter(methodNode -> methodNode.getReturnType() != null)
                .forEach(methodNode -> {
                    final PsiType type = methodNode.getParameterList().getParameters()[0].getType();
                    final PsiTypeParameter typeParameters[] = methodNode.getTypeParameters();
                    final PsiType leftTypes[] = { type };
                    final BiFunction<PsiClass, PsiType, ExtensionMethod> function = (injectNode, injectType) -> {
                        // Type parameters that are successfully inferred from the first parameter need to be discarded, as the first parameter is eliminated so that no type constraints can be imposed on the caller.
                        // Since this leads to potential type safety issues, the type corresponding to these type parameters needs to be replaced with the inferred type.
                        final Set<PsiTypeParameter> dropTypeParameters = type.accept(new IDEAContext.TypeParameterSearcher());
                        // Type parameters are inferred from the actual types(rightTypes) and the original types(leftTypes).
                        final PsiSubstitutor substitutor = resolveHelper.inferTypeArguments(dropTypeParameters.toArray(PsiTypeParameter[]::new), leftTypes, new PsiType[]{ injectType }, languageLevel);
                        final ExtensionMethod lightMethod = { methodNode.getManager(), methodNode.getName(), methodNode };
                        lightMethod
                                .addModifiers(PsiModifier.PUBLIC)
                                .setMethodReturnType(substitutor.substitute(methodNode.getReturnType()));
                        Stream.of(methodNode.getParameterList().getParameters())
                                .skip(1L)
                                .map(parameter -> new LightParameter(lightMethod, parameter.getName(), substitutor.substitute(parameter.getType()), parameter.isVarArgs()))
                                .forEach(lightMethod::addParameter);
                        Stream.of(methodNode.getThrowsList().getReferencedTypes())
                                .map(substitutor::substitute)
                                .map(PsiClassType.class::cast)
                                .forEach(lightMethod::addException);
                        Stream.of(methodNode.getTypeParameters())
                                .filter(targetTypeParameter -> !dropTypeParameters.contains(targetTypeParameter))
                                .forEach(((LightTypeParameterListBuilder) lightMethod.getTypeParameterList())::addParameter);
                        lightMethod.setNavigationElement(methodNode);
                        lightMethod.setContainingClass(injectNode);
                        if (injectNode.isInterface())
                            lightMethod.addModifier(PsiModifier.DEFAULT);
                        lightMethod.setMethodKind(Extension.class.getCanonicalName());
                        return lightMethod;
                    };
                    final ConcurrentHashMap<PsiClass, Map<PsiType, Supplier<ExtensionMethod>>> cache = { };
                    final ExtensionMethodCacheByType cacheByType = {
                            injectType -> TypeConversionUtil.isAssignable(resolveHelper.inferTypeArguments(typeParameters, leftTypes, new PsiType[]{ injectType }, languageLevel).substitute(type), injectType),
                            (injectNode, injectType) -> cache.computeIfAbsent(injectNode, _ -> new ConcurrentHashMap<>()).computeIfAbsent(injectType, _ -> FunctionHelper.lazy(() -> function.apply(injectNode, injectType))).get()
                    };
                    result += cacheByType;
                });
        return result;
    }
    
    private static final String
            CANNOT_INNER_CLASS      = "@Extension cannot be marked on an inner class",
            CAN_ONLY_STATIC_CONTEXT = "The extension owner must be in a static context";
    
    @Override
    public void check(final PsiElement tree, final ProblemsHolder holder, final QuickFixFactory quickFix, final boolean isOnTheFly) {
        if (tree instanceof PsiAnnotation annotation)
            check(annotation, holder, quickFix);
    }
    
    private void check(final PsiAnnotation annotation, final ProblemsHolder holder, final QuickFixFactory quickFix) {
        if (Extension.class.getCanonicalName().equals(annotation.getQualifiedName()))
            if (annotation.getOwner() instanceof PsiModifierList modifierList && modifierList.getParent() instanceof PsiClass owner) {
                if (!checkOwnerPublic(owner))
                    holder.registerProblem(owner, CANNOT_INNER_CLASS, ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotation));
            } else
                holder.registerProblem(annotation, CAN_ONLY_STATIC_CONTEXT, ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotation));
    }
    
    private boolean checkOwnerPublic(final PsiClass owner) {
        @Nullable PsiClass outer = owner;
        while (outer != null) {
            if (!outer.hasModifierProperty(PsiModifier.STATIC))
                return PsiTreeUtil.getContextOfType(outer, PsiClass.class) == null;
            outer = PsiTreeUtil.getContextOfType(outer, PsiClass.class);
        }
        return false;
    }
    
    @Override
    public boolean isImplicitUsage(final PsiElement element, final ImplicitUsageChecker.RefData refData) = element instanceof PsiClass psiClass && isProvider(psiClass) || isExtensionMethodOrSource(element);
    
}
