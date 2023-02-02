package amadeus.maho.lang.idea.handler.base;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiType;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.idea.light.LightBridgeMethod;
import amadeus.maho.lang.idea.light.LightMethod;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.function.FunctionHelper;

import static com.intellij.psi.PsiModifier.*;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ExtensibleMembers {
    
    @ToString
    @EqualsAndHashCode
    public record MethodKey(String name, @Nullable PsiType argTypes[] = null) {
        
        public static MethodKey of(final PsiMethod method) = { method.getName(), Stream.of(method.getParameterList().getParameters()).map(PsiParameter::getType).toArray(PsiType::createArray) };
        
        public static MethodKey recursive(final PsiMethod method) = { method.getName() };
        
    }
    
    @ToString
    @EqualsAndHashCode
    public record Namespace<K, E extends PsiMember>(String name, Class<K> keyType, Class<E> memberType, Function<E, K> keyMapper, Function<E, K> recursiveKeyMapper = keyMapper,
                                                    Function<PsiExtensibleClass, Stream<E>> ownerMembersGetter, Function<PsiExtensibleClass, Stream<E>> implicitAdditionalElementsGetter = _ -> Stream.empty()) {
        
        public Namespace { namespaces += this; }
        
    }
    
    @Getter
    private static final List<Namespace> namespaces = new ArrayList<>();
    
    public static Namespace lookupNamespace(final PsiMember member) = lookupNamespace(member.getClass());
    
    public static Namespace lookupNamespace(final Class<? extends PsiMember> memberType) = namespaces().stream().filter(namespace -> namespace.memberType().isAssignableFrom(memberType)).findFirst().orElseThrow();
    
    public static <E> Function<PsiExtensibleClass, Stream<E>> stream(final Function<PsiExtensibleClass, List<E>> ownerElementGetter, final Predicate<E> predicate = _ -> true)
            = extensible -> ownerElementGetter.apply(extensible).stream().filter(predicate);
    
    public static final Namespace<String, PsiClass>
            INNER_CLASSES = { "innerClasses", String.class, PsiClass.class, PsiClass::getName, stream(PsiExtensibleClass::getOwnInnerClasses) };
    
    public static final Namespace<String, PsiField>
            FIELDS = { "fields", String.class, PsiField.class, PsiField::getName, stream(PsiExtensibleClass::getOwnFields) };
    
    public static final Namespace<MethodKey, LightBridgeMethod>
            BRIDGE_METHODS = { "bridgeMethods", MethodKey.class, LightBridgeMethod.class, MethodKey::of, _ -> Stream.empty() };
    
    public static final Namespace<MethodKey, PsiMethod>
            METHODS = { "methods", MethodKey.class, PsiMethod.class, MethodKey::of, MethodKey::recursive, stream(PsiExtensibleClass::getOwnMethods), ExtensibleMembers::implicitAdditionalMethods };
    
    public static final Namespace<String, PsiRecordComponent>
            RECORD_COMPONENTS = { "recordComponents", String.class, PsiRecordComponent.class, PsiRecordComponent::getName, extensible -> Stream.of(extensible.getRecordHeader()?.getRecordComponents() ?? PsiRecordComponent.EMPTY_ARRAY) };
    
    @Getter
    PsiExtensibleClass extensible;
    
    @Default
    boolean recursive = false;
    
    Map<Namespace, Map<Object, List<PsiMember>>> namespaceNamedMap = namespaces().stream().collect(Collectors.toMap(Function.identity(), FunctionHelper.abandon(HashMap::new), FunctionHelper.first(), IdentityHashMap::new));
    
    Map<Namespace, List<PsiMember>> namespaceMap = namespaces().stream().collect(Collectors.toMap(Function.identity(), FunctionHelper.abandon(ArrayList::new), FunctionHelper.first(), IdentityHashMap::new));
    
    { buildMap(recursive); }
    
    public <K, E extends PsiMember> Map<K, List<E>> map(final Namespace<K, E> namespace) = (Map<K, List<E>>) (Map) namespaceNamedMap[namespace] ?? Map.<K, List<E>>of();
    
    public <K, E extends PsiMember> List<E> list(final Namespace<K, E> namespace) = (List<E>) namespaceMap[namespace] ?? List.<E>of();
    
    public <K, E extends PsiMember> boolean shouldInject(final Namespace<K, E> namespace, final K key) = map(namespace)[key]?.isEmpty() ?? true;
    
    public <K, E extends PsiMember> boolean shouldInject(final E member, final Namespace<K, E> namespace = lookupNamespace(member)) = shouldInject(namespace, namespace.keyMapper().apply(member));
    
    public <K, E extends PsiMember> void inject(final E member, final Namespace<K, E> namespace = lookupNamespace(member)) {
        map(namespace).computeIfAbsent(namespace.keyMapper().apply(member), _ -> new ArrayList<>()) += member;
        list(namespace) += member;
        process(member);
    }
    
    public static LightMethod makeValuesMethod(final PsiExtensibleClass extensible) {
        final LightMethod method = { extensible, "values" };
        method.setNavigationElement(extensible);
        method.mark(ExtensibleMembers.class.getSimpleName());
        method.setContainingClass(extensible);
        method.setMethodReturnType(JavaPsiFacade.getElementFactory(extensible.getProject()).createType(extensible).createArrayType());
        method.addModifiers(PUBLIC, STATIC);
        return method;
    }
    
    public static LightMethod makeValueOfMethod(final PsiExtensibleClass extensible) {
        final LightMethod method = { extensible, "valueOf" };
        method.setNavigationElement(extensible);
        method.mark(ExtensibleMembers.class.getSimpleName());
        method.setContainingClass(extensible);
        method.setMethodReturnType(JavaPsiFacade.getElementFactory(extensible.getProject()).createType(extensible));
        method.addParameter("name", PsiType.getJavaLangString(extensible.getManager(), extensible.getResolveScope()));
        method.addModifiers(PUBLIC, STATIC);
        return method;
    }
    
    public static Stream<PsiMethod> implicitAdditionalMethods(final PsiExtensibleClass extensible)
            = extensible.isEnum() && !isAnonymousClass(extensible) ? Stream.of(makeValuesMethod(extensible), makeValueOfMethod(extensible)) : Stream.empty();
    
    protected static boolean isAnonymousClass(final PsiExtensibleClass extensible) = extensible.getName() == null || extensible instanceof PsiAnonymousClass;
    
    protected void buildMap(final boolean recursive) {
        namespaces().forEach(this::buildNamespace);
        if (!recursive) {
            namespaceMap.values().stream().map(List::copyOf).flatMap(Collection::stream).forEach(this::process);
            process(extensible());
            namespaces().forEach(this::collectAugments);
        }
    }
    
    protected <K, E extends PsiMember> Consumer<E> adder(final Namespace<K, E> namespace, final Function<E, K> keyMapper = recursive ? namespace.recursiveKeyMapper() : namespace.keyMapper(),
            final Map<K, List<E>> map = map(namespace), final List<E> list = list(namespace)) = member -> {
        map.computeIfAbsent(keyMapper.apply(member), _ -> new ArrayList<>()) += member;
        list.add(member);
    };
    
    protected <K, E extends PsiMember> void buildNamespace(final Namespace<K, E> namespace) = Stream.concat(namespace.ownerMembersGetter().apply(extensible()), namespace.implicitAdditionalElementsGetter().apply(extensible()))
            .filter(ExtensibleMembers::namedElement)
            .forEach(adder(namespace));
    
    protected <E extends PsiMember> void process(final E member) {
        final PsiExtensibleClass extensible = extensible();
        HandlerMarker.EntryPoint.process(member, (handler, target, annotation, annotationTree) -> handler.process(target, annotation, annotationTree, this, extensible));
        Syntax.Marker.syntaxHandlers().values().forEach(handler -> handler.process(member, this, extensible));
    }
    
    protected <K, E extends PsiMember> void collectAugments(final Namespace<K, E> namespace) = PsiAugmentProvider.collectAugments(extensible(), namespace.memberType(), null).stream().peek(adder(namespace)).forEach(this::process);
    
    public static boolean namedElement(final PsiElement element) = !incompleteElement(element);
    
    public static boolean incompleteElement(final PsiElement element)
            = element instanceof PsiFieldImpl field && field.getNode().findChildByRoleAsPsiElement(ChildRole.NAME) == null ||
              element instanceof PsiMethodImpl method && Stream.concat(Stream.of(method), Stream.of(method.getParameterList().getParameters())).map(PsiElement::getNode)
                      .cast(CompositeElement.class)
                      .anyMatch(node -> node.findChildByRoleAsPsiElement(ChildRole.NAME) == null);
    
}
