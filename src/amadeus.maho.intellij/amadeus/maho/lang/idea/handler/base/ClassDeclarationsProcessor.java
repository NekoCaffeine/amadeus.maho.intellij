package amadeus.maho.lang.idea.handler.base;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.ScopedClassHierarchy;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;

import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.idea.IDEAContext;
import amadeus.maho.lang.idea.handler.ExtensionHandler;
import amadeus.maho.lang.idea.light.LightBridgeElement;
import amadeus.maho.lang.idea.light.LightBridgeField;
import amadeus.maho.lang.idea.light.LightBridgeMethod;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.DebugHelper;

import static amadeus.maho.lang.idea.IDEAContext.*;
import static com.intellij.psi.impl.PsiClassImplUtil.obtainFinalSubstitutor;

@TransformProvider
public interface ClassDeclarationsProcessor {
    
    @ToString
    @EqualsAndHashCode
    record ProcessDeclarationsContext(boolean qualifier, PsiType type, PsiSubstitutor substitutor) {
        
        public static ProcessDeclarationsContext fromImportStatic(final PsiClass psiClass) = { true, PsiTypesUtil.getClassType(psiClass), PsiSubstitutor.EMPTY };
        
    }
    
    static ProcessDeclarationsContext processDeclarationsContext(final PsiClass psiClass, final PsiElement place) {
        boolean qualifier = false;
        @Nullable PsiType type = null;
        // Try to determine the caller type by context.
        // The reason for getting caller types is the need to distinguish between array types or to infer generics.
        // There's scope for improving the extrapolation process here.
        // Currently the type of reference can only be inferred from the full reference name.
        if (place instanceof PsiMethodCallExpression callExpression) {
            final @Nullable PsiExpression expression = callExpression.getMethodExpression().getQualifierExpression();
            if (expression != null) {
                qualifier = true;
                type = expression.getType();
            }
        } else if (place instanceof PsiReferenceExpression referenceExpression && referenceExpression.getQualifier() != null) {
            final @Nullable PsiExpression expression = referenceExpression.getQualifierExpression();
            if (expression != null) {
                qualifier = true;
                type = expression.getType();
            }
        }
        // If the context type cannot be inferred, it is inferred by the given PsiClass.
        if (type == null)
            type = PsiTypesUtil.getClassType(psiClass);
        // Used to apply the caller's generic infers to its parent class or interface.
        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
        if (type instanceof PsiClassType classType) {
            substitutor = classType.resolveGenerics().getSubstitutor();
        } else {
            final PsiType deepComponentType = type.getDeepComponentType();
            if (deepComponentType instanceof PsiClassType classType)
                substitutor = classType.resolveGenerics().getSubstitutor();
        }
        return { qualifier, type, substitutor };
    }
    
    @Hook(value = PsiClassImplUtil.class, isStatic = true, forceReturn = true)
    private static List<? extends PsiMember> findByMap(final PsiClass context, final @Nullable String name, final boolean checkBases, final PsiClassImplUtil.MemberType memberType) = name == null ? List.of() : checkBases ?
            HandlerSupport.membersCache(context).membersByName(name, MembersCache.memberType(memberType)) : HandlerSupport.extensibleMembers(context).list(ExtensibleMembers.memberType(memberType), name);
    
    @Hook(value = PsiClassImplUtil.class, isStatic = true, forceReturn = true)
    private static List<? extends PsiMember> getAllByMap(final PsiClass context, final PsiClassImplUtil.MemberType memberType) = HandlerSupport.membersCache(context).allMembers(MembersCache.memberType(memberType));
    
    @Hook(value = PsiClassImplUtil.class, isStatic = true, forceReturn = true)
    private static List<Pair<PsiMember, PsiSubstitutor>> getAllWithSubstitutorsByMap(final PsiClass context, final PsiClassImplUtil.MemberType memberType) = withSubstitutors(context, getAllByMap(context, memberType));
    
    @Hook(value = PsiClassImplUtil.class, isStatic = true, forceReturn = true)
    private static List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(final PsiClass context, final String name, final boolean checkBases) = checkBases ?
            withSubstitutors(context, findByMap(context, name, true, PsiClassImplUtil.MemberType.METHOD)) :
            ((List<PsiMethod>) findByMap(context, name, false, PsiClassImplUtil.MemberType.METHOD)).stream()
                    .map(method -> Pair.create(method, PsiSubstitutor.EMPTY))
                    .toList();
    
    private static <T extends PsiMember> List<Pair<T, PsiSubstitutor>> withSubstitutors(final PsiClass context, final List<? extends PsiMember> members) {
        final ScopedClassHierarchy hierarchy = (Privilege) ScopedClassHierarchy.getHierarchy(context, context.getResolveScope());
        final LanguageLevel level = PsiUtil.getLanguageLevel(context);
        return members.stream().map(member -> {
            final @Nullable PsiClass containingClass = member.getContainingClass();
            return Pair.create((T) member, containingClass == null ? PsiSubstitutor.EMPTY : (Privilege) hierarchy.getSuperMembersSubstitutor(containingClass, level) ?? PsiSubstitutor.EMPTY);
        }).toList();
    }
    
    Map<ElementClassHint.DeclarationKind, Class<? extends PsiMember>> KINDS = Map.of(
            ElementClassHint.DeclarationKind.FIELD, PsiField.class,
            ElementClassHint.DeclarationKind.METHOD, PsiMethod.class,
            ElementClassHint.DeclarationKind.CLASS, PsiClass.class
    );
    
    @Hook(value = PsiClassImplUtil.class, isStatic = true, forceReturn = true)
    private static boolean processAllMembersWithoutSubstitutors(final PsiClass context, final PsiScopeProcessor processor, final ResolveState state) {
        final @Nullable String name = processor.getHint(NameHint.KEY)?.getName(state) ?? null;
        final @Nullable ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
        final Predicate<PsiMember> memberProcessor = member -> processor.execute(member, state);
        final MembersCache cache = HandlerSupport.membersCache(context);
        return KINDS.entrySet().stream().allMatch(entry -> !(classHint?.shouldProcess(entry.getKey()) ?? true) || processMembers(cache, entry.getValue(), name, memberProcessor)) &&
               processImportableBridgeElements(context, processor, state, name);
    }
    
    @Hook(value = PsiClassImplUtil.class, isStatic = true, forceReturn = true)
    private static boolean processDeclarationsInClass(
            final PsiClass context,
            final PsiScopeProcessor processor,
            final ResolveState state,
            final Set<PsiClass> visited,
            final @Nullable PsiElement last,
            final PsiElement place,
            final LanguageLevel level,
            final boolean isRaw) {
        if (last instanceof PsiTypeParameterList || last instanceof PsiModifierList && context.getModifierList() == last || visited != null && !visited.add(context))
            return true;
        final @Nullable ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
        final boolean shouldProcessClasses = classHint?.shouldProcess(ElementClassHint.DeclarationKind.CLASS) ?? true;
        if (shouldProcessClasses) {
            if (last != null && last.getContext() == context) {
                if (last instanceof PsiClass && !processor.execute(last, state))
                    return false;
                final @Nullable PsiTypeParameterList list = context.getTypeParameterList();
                if (list != null && !list.processDeclarations(processor, ResolveState.initial(), last, place))
                    return false;
            }
        }
        if (last instanceof PsiReferenceList)
            return true;
        ProgressIndicatorProvider.checkCanceled();
        final GlobalSearchScope resolveScope = place.getResolveScope();
        final PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
        final boolean raw = isRaw || PsiUtil.isRawSubstitutor(context, substitutor);
        final @Nullable String name = processor.getHint(NameHint.KEY)?.getName(state) ?? null;
        final Function<PsiMember, PsiSubstitutor> substitutorFunction = substitutor(context, raw, resolveScope, level, substitutor);
        final Predicate<PsiMember> memberProcessor = member -> {
            final @Nullable PsiClass containingClass = member.getContainingClass();
            if (containingClass == null) {
                DebugHelper.breakpoint();
                return true;
            }
            processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, containingClass);
            return processor.execute(member, containingClass == context ? state : state.put(PsiSubstitutor.KEY, substitutorFunction.apply(member)));
        };
        final boolean shouldProcessFields = classHint?.shouldProcess(ElementClassHint.DeclarationKind.FIELD) ?? true;
        final MembersCache cache = HandlerSupport.membersCache(context);
        { // Field
            if (shouldProcessFields)
                if (!processMembers(cache, PsiField.class, name, memberProcessor))
                    return false;
        }
        { // Class
            if (shouldProcessClasses) {
                if (!processMembers(cache, PsiClass.class, name, memberProcessor))
                    return false;
            }
        }
        final boolean shouldProcessMethods = classHint?.shouldProcess(ElementClassHint.DeclarationKind.METHOD) ?? true;
        { // Method
            if (shouldProcessMethods) {
                final Predicate<PsiMethod> methodFilter = processor instanceof MethodResolverProcessor resolverProcessor ? method -> method.isConstructor() == resolverProcessor.isConstructor() : _ -> true;
                if (!processMembers(cache, PsiMethod.class, name, memberProcessor, methodFilter))
                    return false;
            }
        }
        if (shouldProcessFields || shouldProcessMethods)
            if (requiresMaho(place)) { // Maho
                if (!(processor instanceof MethodResolverProcessor methodResolverProcessor) || !methodResolverProcessor.isConstructor()) {
                    final ProcessDeclarationsContext pdc = processDeclarationsContext(context, place);
                    { // Extension
                        if (shouldProcessMethods) {
                            final List<PsiClass> supers = supers(context);
                            final Collection<PsiMethod> collect = supers.stream().map(IDEAContext::methods).flatMap(Collection::stream).collect(Collectors.toSet());
                            // The purpose of differentiating providers is to report errors when multiple providers provide the same signature.
                            final HashMap<PsiClass, Collection<PsiMethod>> providerRecord = { };
                            if (!IDEAContext.computeReadActionIgnoreDumbMode(() -> {
                                final Collection<ExtensionHandler.ExtensionMethod> extensionMethods = ExtensionHandler.memberCache(resolveScope, context, pdc.type(), supers, pdc.substitutor());
                                return (name == null ? extensionMethods.stream() : extensionMethods.stream().filter(method -> name.equals(method.getName())))
                                        .filter(method -> ExtensionHandler.checkMethod(collect, providerRecord, method))
                                        .allMatch(method -> processor.execute(method, state));
                            }))
                                return false;
                        }
                    }
                    { // Bridge
                        return processBridgeElements(context, processor, state, pdc, name, shouldProcessFields, shouldProcessMethods);
                    }
                }
            }
        return true;
    }
    
    private static boolean processImportableBridgeElements(final PsiClass context, final PsiScopeProcessor processor, final ResolveState state, final @Nullable String name)
            = processBridgeElements(context, processor, state, ProcessDeclarationsContext.fromImportStatic(context), name, true, true, true);
    
    private static boolean processBridgeElements(final PsiClass context, final PsiScopeProcessor processor, final ResolveState state, final ProcessDeclarationsContext pdc,
            final @Nullable String name, final boolean shouldProcessFields, final boolean shouldProcessMethods, final boolean staticOnly = false) {
        final List<? extends LightBridgeElement> bridgeElements = Stream.concat(Stream.of(context), supers(context).stream())
                .cast(PsiExtensibleClass.class)
                .map(HandlerSupport::extensibleMembers)
                .flatMap(members -> members.bridgeElements(pdc, staticOnly))
                .filter(member -> name?.equals(member.getName()) ?? true)
                .toList();
        return Stream.<PsiMember>concat(
                        shouldProcessFields ? bridgeElements.stream().cast(LightBridgeField.class)
                                .filter(it -> checkStaticError(context.findFieldByName(it.getName(), true), it)) : Stream.empty(),
                        shouldProcessMethods ? bridgeElements.stream().cast(LightBridgeMethod.class)
                                .filter(it -> checkStaticError(context.findMethodBySignature(it, true), it)) : Stream.empty())
                .allMatch(member -> processor.execute(member, state));
    }
    
    private static boolean checkStaticError(final @Nullable PsiMember member, final LightBridgeElement bridge) = member == null || !member.hasModifierProperty(PsiModifier.STATIC) && bridge.hasModifierProperty(PsiModifier.STATIC);
    
    private static <T extends PsiMember> boolean processMembers(final MembersCache cache, final Class<T> memberType, final @Nullable String name, final Predicate<? super T> memberProcessor, final Predicate<T> filter = _ -> true)
            = cache.membersByName(name, memberType).stream().filter(filter).allMatch(memberProcessor);
    
    private static Function<PsiMember, PsiSubstitutor> substitutor(final PsiClass context, final boolean isRaw, final GlobalSearchScope resolveScope, final LanguageLevel languageLevel, final PsiSubstitutor substitutor) {
        final ScopedClassHierarchy hierarchy = (Privilege) ScopedClassHierarchy.getHierarchy(context, resolveScope);
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
        return member -> {
            final @Nullable PsiClass containingClass = member.getContainingClass();
            final PsiSubstitutor finalSubstitutor = containingClass == null || member.hasModifierProperty(PsiModifier.STATIC) ? substitutor :
                    obtainFinalSubstitutor(containingClass, (Privilege) hierarchy.getSuperMembersSubstitutor(containingClass, languageLevel) ?? PsiSubstitutor.EMPTY, context, substitutor, factory, languageLevel);
            return member instanceof PsiMethod method ? checkRaw(isRaw, factory, method, finalSubstitutor) : finalSubstitutor;
        };
    }
    
    private static PsiSubstitutor checkRaw(final boolean isRaw, final PsiElementFactory factory, final PsiMethod candidateMethod, final PsiSubstitutor substitutor)
            = isRaw && !candidateMethod.hasModifierProperty(PsiModifier.STATIC) && candidateMethod.getContainingClass() instanceof PsiClass containingClass && containingClass.hasTypeParameters() ?
            factory.createRawSubstitutor(substitutor, candidateMethod.getTypeParameters()) : substitutor;
    
}
