package amadeus.maho.lang.idea.handler.base;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.util.containers.JBTreeTraverser;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MembersCache {
    
    private static final List<ExtensibleMembers.Namespace<?, ?>> NAMESPACES = List.of(ExtensibleMembers.INNER_CLASSES, ExtensibleMembers.FIELDS, ExtensibleMembers.METHODS);
    
    PsiClass context;
    
    @Default
    boolean recursive = false;
    
    List<PsiClass> allSupers = JBTreeTraverser.<PsiClass>from(c -> List.of(c.getSupers())).unique().withRoot(context).toList();
    
    List<ExtensibleMembers> extensibleMembers = allSupers.stream()
            .map(c -> HandlerSupport.delayExtensibleMembers(c, recursive).members())
            .toList();
    
    boolean complete = recursive || extensibleMembers.stream().noneMatch(ExtensibleMembers::recursive);
    
    Map<Class<? extends PsiMember>, List<PsiMember>> allMembers = extensibleMembers.stream()
            .flatMap(members -> NAMESPACES.stream().map(members::list).flatMap(List::stream))
            .collect(Collectors.groupingBy(MembersCache::memberType));
    
    Map<String, Map<Class<? extends PsiMember>, List<PsiMember>>> membersByName = allMembers.values().stream()
            .flatMap(Collection::stream)
            .collect(Collectors.groupingBy(member -> member.getName() ?? "", Collectors.groupingBy(MembersCache::memberType)));
    
    public <T extends PsiMember> List<T> allMembers(final Class<T> type) = (List<T>) (allMembers()[type] ?? List.of());
    
    public <T extends PsiMember> List<T> membersByName(final @Nullable String name, final Class<T> type) = name == null ? allMembers(type) : (List<T>) (membersByName()[name]?.get(type) ?? List.<T>of());
    
    public List<PsiMember> membersByName(final String name) = membersByName()[name]?.values().stream().flatMap(List::stream).toList() ?? List.<PsiMember>of();
    
    public static Class<? extends PsiMember> memberType(final PsiMember member) = switch (member) {
        case PsiField _  -> PsiField.class;
        case PsiMethod _ -> PsiMethod.class;
        default          -> PsiClass.class;
    };
    
    public static Class<? extends PsiMember> memberType(final PsiClassImplUtil.MemberType memberType) = switch (memberType) {
        case FIELD  -> PsiField.class;
        case METHOD -> PsiMethod.class;
        default     -> PsiClass.class;
    };
    
}
