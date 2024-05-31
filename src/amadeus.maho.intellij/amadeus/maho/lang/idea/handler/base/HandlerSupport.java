package amadeus.maho.lang.idea.handler.base;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.ResolveState;
import com.intellij.psi.TypeAnnotationProvider;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.source.ClassInnerStuffCache;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;

import amadeus.maho.core.Maho;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.idea.IDEAContext;
import amadeus.maho.lang.idea.handler.AccessibleHandler;
import amadeus.maho.lang.idea.light.LightBridgeElement;
import amadeus.maho.lang.idea.light.LightBridgeField;
import amadeus.maho.lang.idea.light.LightBridgeMethod;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;
import amadeus.maho.util.control.LinkedIterator;
import amadeus.maho.util.control.OverrideMap;
import amadeus.maho.util.dynamic.ClassLocal;
import amadeus.maho.util.dynamic.InvokeContext;
import amadeus.maho.util.function.Consumer4;
import amadeus.maho.util.runtime.ObjectHelper;
import amadeus.maho.util.runtime.TypeHelper;
import amadeus.maho.util.tuple.Tuple2;

import static amadeus.maho.lang.idea.IDEAContext.*;

@TransformProvider
public interface HandlerSupport {
    
    class Augmenter extends PsiAugmentProvider {
        
        @Override
        protected @Nullable PsiType inferType(final PsiTypeElement typeElement) = CachedValuesManager.getProjectPsiDependentCache(typeElement,
                it -> new PsiType[]{ null }.let(result -> Syntax.Marker.syntaxHandlers().values().forEach(handler -> handler.inferType(it, result)))[0]);
        
        private static final ThreadLocal<LinkedList<PsiModifierList>> transformModifiersContextLocal = ThreadLocal.withInitial(LinkedList::new);
        
        @Override
        protected Set<String> transformModifiers(final PsiModifierList modifierList, final Set<String> modifiers) {
            if (modifierList.getParent() instanceof PsiModifierListOwner owner) {
                final var context = transformModifiersContextLocal.get();
                if (!context.contains(modifierList)) {
                    context.addLast(modifierList);
                    try {
                        return AccessibleHandler.transformPackageLocalToProtected(modifierList.getParent(), requiresMaho(modifierList) ?
                                new HashSet<>(modifiers).let(result -> process(owner, (handler, tree, annotation, annotationTree) -> handler.transformModifiers(tree, annotation, annotationTree, result))) : new HashSet<>(modifiers));
                    } finally { context.removeLast(); }
                }
            }
            return modifiers;
        }
        
    }
    
    ThreadLocal<LinkedList<PsiClass>> collectMemberContextLocal = ThreadLocal.withInitial(LinkedList::new);
    
    Key<CachedValue<ConcurrentWeakIdentityHashMap<PsiExtensibleClass, DelayExtensibleMembers>>> members = { "members" }, recursiveMembers = { "recursiveMembers" };
    
    static ConcurrentWeakIdentityHashMap<PsiExtensibleClass, DelayExtensibleMembers> membersCache(final Project project, final boolean recursive = false)
            = CachedValuesManager.getManager(project).getCachedValue(project, recursive ? recursiveMembers : members,
            () -> CachedValueProvider.Result.create(new ConcurrentWeakIdentityHashMap<>(), PsiModificationTracker.getInstance(project)), false);
    
    private static ExtensibleMembers members(final ClassInnerStuffCache cache) = members((Privilege) cache.myClass);
    
    record DelayExtensibleMembers(PsiExtensibleClass extensible, boolean recursive, ReentrantLock lock = { }, AtomicReference<ExtensibleMembers> reference = { }) {
        
        public ExtensibleMembers members(final boolean shouldLock = ASTTransformer.collectGuard.get().get() == 0 && false) {
            if (shouldLock)
                lock.lock();
            try {
                @Nullable ExtensibleMembers members = reference.get();
                if (members == null)
                    reference.set(members = { extensible, recursive });
                return members;
            } finally {
                if (shouldLock)
                    lock.unlock();
            }
        }
        
    }
    
    private static ExtensibleMembers awaitMembers(final PsiExtensibleClass extensible, final boolean recursive = false)
            = membersCache(extensible.getProject(), recursive).computeIfAbsent(extensible, it -> new DelayExtensibleMembers(it, recursive)).members();
    
    private static ExtensibleMembers members(final PsiExtensibleClass extensible) {
        if (!accessSourceAST()) {
            final var context = collectMemberContextLocal.get();
            if (!context[extensible]) {
                context << extensible;
                try {
                    return awaitMembers(extensible);
                } finally { context--; }
            }
        }
        return awaitMembers(extensible, true);
    }
    
    @Hook
    private static Hook.Result getFields(final ClassInnerStuffCache $this) = { members($this).list(ExtensibleMembers.FIELDS).toArray(PsiField.EMPTY_ARRAY) };
    
    @Hook
    private static Hook.Result getMethods(final ClassInnerStuffCache $this) = { members($this).list(ExtensibleMembers.METHODS).toArray(PsiMethod.EMPTY_ARRAY) };
    
    @Hook
    private static Hook.Result getConstructors(final ClassInnerStuffCache $this) = { members($this).list(ExtensibleMembers.METHODS).stream().filter(PsiMethod::isConstructor).toArray(PsiMethod.ARRAY_FACTORY::create) };
    
    @Hook
    private static Hook.Result getInnerClasses(final ClassInnerStuffCache $this) = { members($this).list(ExtensibleMembers.INNER_CLASSES).toArray(PsiClass.EMPTY_ARRAY) };
    
    @Hook
    private static Hook.Result findFieldByName(final ClassInnerStuffCache $this, final String name, final boolean checkBases)
            = checkBases ? Hook.Result.VOID : new Hook.Result(members($this).map(ExtensibleMembers.FIELDS)[name]?.stream()?.findFirst()?.orElse(null) ?? null);
    
    @Hook
    private static Hook.Result findMethodsByName(final ClassInnerStuffCache $this, final String name, final boolean checkBases)
            = checkBases ? Hook.Result.VOID : new Hook.Result(members($this).list(ExtensibleMembers.METHODS).stream().filter(method -> method.getName().equals(name)).toArray(PsiMethod.ARRAY_FACTORY::create));
    
    @Hook
    private static Hook.Result findInnerClassByName(final ClassInnerStuffCache $this, final String name, final boolean checkBases)
            = checkBases ? Hook.Result.VOID : new Hook.Result(members($this).map(ExtensibleMembers.INNER_CLASSES)[name]?.stream()?.findFirst()?.orElse(null) ?? null);
    
    @ToString
    @EqualsAndHashCode
    record ProcessDeclarationsContext(boolean qualifier, PsiType type, PsiSubstitutor substitutor) { }
    
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
    
    @Hook(value = PsiClassImplUtil.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static boolean processDeclarationsInClass(
            final boolean capture,
            final PsiClass psiClass,
            final PsiScopeProcessor processor,
            final ResolveState state,
            final @Nullable Set<PsiClass> visited,
            final @Nullable PsiElement last,
            final PsiElement place,
            final LanguageLevel languageLevel,
            final boolean isRaw,
            final GlobalSearchScope resolveScope) {
        if (!capture)
            return false;
        if (psiClass instanceof PsiExtensibleClass extensible && requiresMaho(psiClass)) {
            if (last instanceof PsiTypeParameterList || last instanceof PsiModifierList && psiClass.getModifierList() == last || visited != null && visited.contains(psiClass))
                return true;
            if (!(processor instanceof MethodResolverProcessor methodResolverProcessor) || !methodResolverProcessor.isConstructor()) {
                final ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
                final @Nullable NameHint nameHint = processor.getHint(NameHint.KEY);
                final @Nullable String name = nameHint?.getName(state) ?? null;
                final boolean shouldProcessFields = classHint?.shouldProcess(ElementClassHint.DeclarationKind.FIELD) ?? true && (name == null || psiClass.findFieldByName(name, true) == null),
                        shouldProcessMethods = classHint?.shouldProcess(ElementClassHint.DeclarationKind.METHOD) ?? true;
                if (shouldProcessFields || shouldProcessMethods) {
                    final ProcessDeclarationsContext context = processDeclarationsContext(psiClass, place);
                    final List<? extends LightBridgeElement> bridgeElements = Stream.concat(Stream.of(extensible), supers(extensible).stream())
                            .cast(PsiExtensibleClass.class)
                            .map(HandlerSupport::members)
                            .flatMap(members -> members.bridgeElements(context))
                            .filter(member -> name?.equals(member.getName()) ?? true)
                            .toList();
                    return Stream.<PsiMember>concat(
                                    shouldProcessFields ? bridgeElements.stream().cast(LightBridgeField.class)
                                            .filter(it -> name != null || psiClass.findFieldByName(it.getName(), true) == null) : Stream.empty(),
                                    shouldProcessMethods ? bridgeElements.stream().cast(LightBridgeMethod.class)
                                            .filter(it -> psiClass.findMethodBySignature(it, true) == null) : Stream.empty())
                            .allMatch(member -> processor.execute(member, state));
                }
            }
        }
        return true;
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static JavaResolveResult[] resolveToMethod(final JavaResolveResult capture[], final PsiReferenceExpressionImpl $this, final PsiFile containingFile) {
        if (capture.length > 1) {
            final JavaResolveResult results[] = Stream.of(capture)
                    .filterNot(result -> result.getElement() instanceof LightBridgeMethod)
                    .toArray(JavaResolveResult[]::new);
            return results.length == 0 ? capture : results;
        }
        return capture;
    }
    
    @Hook(value = JavaSharedImplUtil.class, isStatic = true, forceReturn = true)
    private static PsiType getType(final PsiTypeElement typeElement, final PsiElement anchor, final @Nullable PsiAnnotation stopAt) {
        @RequiredArgsConstructor
        @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
        class Key {
            
            PsiElement anchor;
            
            @Nullable
            PsiAnnotation stopAt;
            
            @Override
            public int hashCode() = ObjectHelper.hashCode(anchor, stopAt);
            
            @Override
            public boolean equals(final Object obj) = obj instanceof Key key && anchor == key.anchor && stopAt == key.stopAt;
            
        }
        final ConcurrentHashMap<Key, PsiType> cache = CachedValuesManager.getProjectPsiDependentCache(typeElement, _ -> new ConcurrentHashMap<>());
        final Key key = { anchor, stopAt };
        final @Nullable PsiType type = cache[key];
        if (type != null)
            return type;
        final PsiType result = wrapTypeIfNecessary(typeElement, anchor, stopAt, true);
        cache[key] = result;
        return result;
    }
    
    static PsiType wrapTypeIfNecessary(final PsiTypeElement typeElement, final PsiElement anchor, final @Nullable PsiAnnotation stopAt, final boolean wrap) {
        final PsiType p_type[] = { typeElement.getType() };
        ((Privilege) JavaSharedImplUtil.collectAnnotations(anchor, stopAt))?.forEach(annotations -> p_type[0] = p_type[0].createArrayType().annotate(TypeAnnotationProvider.Static.create(annotations)));
        return wrap && typeElement.getParent() instanceof PsiModifierListOwner owner && requiresMaho(owner) ? p_type.let(result -> process(owner,
                (handler, target, annotation, annotationTree) -> handler.wrapperType(typeElement, annotation, annotationTree, result)))[0] : p_type[0];
    }
    
    @Hook(value = PsiTypesUtil.class, isStatic = true)
    private static Hook.Result getExpectedTypeByParent(final PsiElement element) {
        final @Nullable PsiType expectedTypeByParent = expectedTypeByParent(element);
        if (expectedTypeByParent != null)
            return { expectedTypeByParent };
        return Hook.Result.VOID;
    }
    
    private static @Nullable PsiType expectedTypeByParent(final PsiElement element) {
        if (PsiUtil.skipParenthesizedExprUp(element.getParent()) instanceof PsiVariable variable && (Privilege) PsiUtil.checkSameExpression(element, variable.getInitializer())) {
            final @Nullable PsiTypeElement typeElement = variable.getTypeElement();
            if (typeElement != null) {
                if (typeElement.isInferredType())
                    return null;
                final @Nullable PsiIdentifier identifier = variable.getNameIdentifier();
                if (identifier == null)
                    return null;
                return wrapTypeIfNecessary(typeElement, identifier, null, false);
            }
        }
        return null;
    }
    
    @Hook(value = HighlightUtil.class, isStatic = true, at = @At(method = @At.MethodInsn(name = "getType"), ordinal = 0), before = false, capture = true)
    private static PsiType checkVariableInitializerType(final PsiType capture, final PsiVariable variable) = unwrapTypeOrNull(variable) ?? capture;
    
    static @Nullable PsiType unwrapType(final PsiVariable variable) = unwrapTypeOrNull(variable) ?? variable.getType();
    
    static @Nullable PsiType unwrapTypeOrNull(final PsiVariable variable) = CachedValuesManager.getProjectPsiDependentCache(variable, it -> {
        final @Nullable PsiTypeElement typeElement = variable.getTypeElement();
        final @Nullable PsiIdentifier identifier = variable.getNameIdentifier();
        return typeElement != null && identifier != null ? wrapTypeIfNecessary(typeElement, identifier, null, false) : null;
    });
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    static PsiClass[] getInterfaces(final PsiClass capture[], final PsiClassImpl $this) = requiresMaho($this) ? IDEAContext.computeReadActionIgnoreDumbMode(() -> new HashSet<>(List.of(capture)).let(
            result -> process($this, (handler, tree, annotation, annotationTree) -> handler.transformInterfaces(tree, annotation, annotationTree, result))).toArray(PsiClass[]::new)) : capture;
    
    @Hook(value = PsiClassImplUtil.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    static PsiClassType[] getImplementsListTypes(final PsiClassType capture[], final PsiClass $this) = requiresMaho($this) ? IDEAContext.computeReadActionIgnoreDumbMode(() -> new HashSet<>(List.of(capture)).let(
            result -> process($this, (handler, tree, annotation, annotationTree) -> handler.transformInterfaceTypes(tree, annotation, annotationTree, result))).toArray(PsiClassType[]::new)) : capture;
    
    OverrideMap overrideMap = { };
    
    ConcurrentWeakIdentityHashMap<Method, Method> redirectCache = { };
    
    static Method lambdaRedirect(final Method method, final Predicate<Method> predicate = _ -> true) = redirectCache.computeIfAbsent(method, it -> {
        final MethodNode methodNode = Maho.getMethodNodeFromMethodNonNull(it);
        if (methodNode.instructions == null)
            return it;
        final Class<?> declaringClass = it.getDeclaringClass();
        final ClassLoader loader = declaringClass.getClassLoader();
        return ~new LinkedIterator<>(AbstractInsnNode::getPrevious, methodNode.instructions.getLast()).stream()
                .cast(MethodInsnNode.class)
                .map(insn -> ASMHelper.loadMethod(Type.getObjectType(insn.owner), insn.name, insn.desc, loader))
                .filterNot(TypeHelper::isBoxedMethod) ?? it;
    });
    
    ClassLocal<Method> invokeTarget = { it -> lambdaRedirect(it.constantPool().lastMethodWithoutBoxed()) };
    
    static <T extends PsiModifierListOwner> void process(final T tree, final Class<? extends Annotation> annotationType, final Consumer4<BaseHandler<Annotation>, ? super T, Annotation, PsiAnnotation> consumer)
            = process(tree, it -> it.handler().value() == annotationType, consumer);
    
    static <T extends PsiModifierListOwner> void process(final T tree, final Predicate<BaseHandler<?>> predicate = _ -> true, final Consumer4<BaseHandler<Annotation>, ? super T, Annotation, PsiAnnotation> consumer)
            = overrideMap[Handler.Marker.baseHandlers()][BaseHandler.Methods.specific(invokeTarget[consumer.getClass()], tree)].stream()
            .filter(predicate)
            .map(baseHandler -> getAnnotationsByTypeWithOuter(tree, baseHandler))
            .nonnull()
            .sorted((a, b) -> (int) (a.getKey().handler().priority() - b.getKey().handler().priority()))
            .forEach(entry -> entry.getValue().forEach(annotation -> consumer.accept(entry.getKey(), tree, annotation.v1, annotation.v2)));
    
    static boolean hasAnnotation(final PsiModifierListOwner tree, final Class<? extends Annotation> annotationType) = !getAnnotationsByTypeWithOuter(tree, annotationType).isEmpty();
    
    static boolean hasAnnotation(final PsiModifierListOwner tree, final BaseHandler<?> baseHandler) = getAnnotationsByTypeWithOuter(tree, baseHandler) != null;
    
    static <A extends Annotation> List<Tuple2<A, PsiAnnotation>> getAnnotationsByTypeWithOuter(final PsiModifierListOwner tree, final Class<A> annotationType) {
        for (final BaseHandler<Annotation> baseHandler : Handler.Marker.baseHandlers())
            if (baseHandler.handler().value() == annotationType)
                return getAnnotationsByTypeWithOuter(tree, (BaseHandler<A>) baseHandler)?.getValue() ?? List.<Tuple2<A, PsiAnnotation>>of();
        return getAnnotationsByType(tree, annotationType);
    }
    
    Map.Entry emptyEntry = new Map.Entry() {
        
        @Override
        public @Nullable Object getKey() = null;
        
        @Override
        public @Nullable Object getValue() = null;
        
        @Override
        public @Nullable Object setValue(final Object value) = null;
        
    };
    
    static @Nullable <A extends Annotation> Map.Entry<BaseHandler<A>, List<Tuple2<A, PsiAnnotation>>> getAnnotationsByTypeWithOuter(final PsiModifierListOwner tree, final BaseHandler<A> baseHandler) {
        final var cache = CachedValuesManager.<PsiModifierListOwner, Map<BaseHandler<A>, Map.Entry<BaseHandler<A>, List<Tuple2<A, PsiAnnotation>>>>>getProjectPsiDependentCache(tree, _ -> new ConcurrentHashMap<>());
        @Nullable Map.Entry<BaseHandler<A>, List<Tuple2<A, PsiAnnotation>>> result = cache[baseHandler];
        if (result != null)
            return result == emptyEntry ? null : result;
        final List<Tuple2<A, PsiAnnotation>> annotations = getAnnotationsByType(tree, (Class<A>) baseHandler.handler().value());
        if (annotations.isEmpty()) {
            final Handler.Range ranges[] = baseHandler.handler().ranges();
            if (ranges.length > 0) {
                final @Nullable PsiClass outer = PsiTreeUtil.getContextOfType(tree, PsiClass.class);
                if (outer != null)
                    for (final Handler.Range range : ranges) {
                        if (checkRange(range, tree))
                            if (baseHandler.derivedFilter(tree)) {
                                final List<Tuple2<A, PsiAnnotation>> outerAnnotations = getAnnotationsByType(outer, (Class<A>) baseHandler.handler().value());
                                result = outerAnnotations.isEmpty() ? emptyEntry : Map.entry(baseHandler, outerAnnotations);
                                break;
                            } else
                                break;
                    }
            }
        }
        cache[baseHandler] = result = result ?? (annotations.isEmpty() ? emptyEntry : Map.entry(baseHandler, annotations));
        return result == emptyEntry ? null : result;
    }
    
    static <A extends Annotation> @Nullable A lookupAnnotation(final PsiModifierListOwner owner, final Class<A> annotationType)
            = getAnnotationsByTypeWithOuter(owner, annotationType).stream().findFirst().map(Tuple2::v1).orElse(null);
    
    ThreadLocal<AtomicInteger> accessSourceASTCounterLocal = ThreadLocal.withInitial(AtomicInteger::new);
    
    ThreadLocal<InvokeContext> accessSourceASTContextLocal = ThreadLocal.withInitial(() -> new InvokeContext(accessSourceASTCounterLocal.get()));
    
    static boolean accessSourceAST() = accessSourceASTCounterLocal.get().get() > 0;
    
    static <A extends Annotation> List<Tuple2<A, PsiAnnotation>> getAnnotationsByType(final PsiModifierListOwner tree, final Class<A> annotationType) {
        if (tree.getAnnotations().length == 0 && (!(tree instanceof PsiExtensibleClass) || !annotationType.isAnnotationPresent(Inherited.class)))
            return List.of();
        final var cache = CachedValuesManager.<PsiModifierListOwner, Map<Class<A>, List<Tuple2<A, PsiAnnotation>>>>getProjectPsiDependentCache(tree, _ -> new ConcurrentHashMap<>());
        @Nullable List<Tuple2<A, PsiAnnotation>> result = cache[annotationType]; // avoid computeIfAbsent deadlock
        if (result == null)
            cache[annotationType] = result = accessSourceASTContextLocal.get() ^ () -> {
                final List<Tuple2<A, PsiAnnotation>> annotations = getAnnotationsByType(tree.getProject(), annotationType, tree.getAnnotations());
                if (tree instanceof PsiExtensibleClass extensibleClass && annotationType.isAnnotationPresent(Inherited.class) && extensibleClass.getSuperClass() instanceof PsiExtensibleClass parent)
                    annotations *= getAnnotationsByType(parent, annotationType);
                return annotations;
            };
        return result;
    }
    
    static <A extends Annotation> List<Tuple2<A, PsiAnnotation>> getAnnotationsByType(final Project project, final Class<A> annotationType, final PsiAnnotation... annotations) {
        final LinkedList<Tuple2<A, PsiAnnotation>> result = { };
        final String canonicalName = annotationType.getCanonicalName(), simpleName = annotationType.getSimpleName();
        for (final PsiAnnotation annotation : annotations)
            if (annotation.isValid() && checkAnnotationType(canonicalName, simpleName, annotation)) {
                final @Nullable A instance = AnnotationInvocationHandler.make(annotationType, annotation.getParameterList().getAttributes().length == 0 ? null : annotation);
                if (instance == null)
                    continue;
                final Tuple2<A, PsiAnnotation> tuple = { instance, annotation };
                result << tuple;
            }
        return result;
    }
    
    private static Set<String> importClassNames(final PsiJavaFile file) = file instanceof PsiJavaFileImpl ? CachedValuesManager.getProjectPsiDependentCache(file, it -> {
        final @Nullable PsiImportList importList = it.getImportList();
        return importList != null ? Stream.of(importList.getImportStatements()).map(PsiImportStatement::getQualifiedName).collect(Collectors.toSet()) : Set.of();
    }) : Set.of();
    
    static boolean checkAnnotationType(final String canonicalName, final String simpleName, final PsiAnnotation annotation) {
        if (annotation.isValid()) {
            final @Nullable PsiJavaCodeReferenceElement reference = annotation.getNameReferenceElement();
            final boolean valid = reference != null && reference.isValid();
            if (valid)
                if (simpleName.equals(reference.getReferenceName())) {
                    final @Nullable PsiJavaFile file = PsiTreeUtil.getParentOfType(annotation, PsiJavaFile.class);
                    if (file instanceof PsiJavaFileImpl) {
                        final String referenceText = reference.getText();
                        final int index = referenceText.indexOf('.');
                        return index == -1 ? importClassNames(file)[canonicalName] :
                                canonicalName.endsWith(referenceText) && importClassNames(file)[canonicalName.substring(0, canonicalName.length() - (referenceText.length() - index))];
                    }
                }
            return IDEAContext.computeReadActionIgnoreDumbMode(() -> {
                if (canonicalName.equals(annotation.getQualifiedName()))
                    return true;
                if (valid) {
                    final JavaResolveResult result = reference.advancedResolve(true);
                    final @Nullable PsiElement element = result.getElement();
                    if (element instanceof PsiClass psiClass)
                        return canonicalName.equals(psiClass.getQualifiedName());
                }
                return false;
            });
        }
        return false;
    }
    
    private static boolean checkRange(final Handler.Range range, final PsiElement tree) = switch (tree) {
        case PsiField ignored  -> range == Handler.Range.FIELD;
        case PsiMethod ignored -> range == Handler.Range.METHOD;
        case PsiClass ignored  -> range == Handler.Range.CLASS;
        case null,
             default     -> false;
    };
    
}
