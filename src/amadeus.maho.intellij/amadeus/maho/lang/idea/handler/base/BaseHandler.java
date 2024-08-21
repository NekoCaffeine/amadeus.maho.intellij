package amadeus.maho.lang.idea.handler.base;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.transform.mark.base.TransformProvider;

@Getter
@TransformProvider
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public abstract non-sealed class BaseHandler<A extends Annotation> implements Comparable<BaseHandler<A>>, InspectionTool.Checker {
    
    @SneakyThrows
    public interface Methods {
        
        Method
                process                = BaseHandler.class.getMethod("process", PsiElement.class, Annotation.class, PsiAnnotation.class, ExtensibleMembers.class, PsiClass.class),
                processVariable        = BaseHandler.class.getMethod("processVariable", PsiField.class, Annotation.class, PsiAnnotation.class, ExtensibleMembers.class, PsiClass.class),
                processMethod          = BaseHandler.class.getMethod("processMethod", PsiMethod.class, Annotation.class, PsiAnnotation.class, ExtensibleMembers.class, PsiClass.class),
                processClass           = BaseHandler.class.getMethod("processClass", PsiClass.class, Annotation.class, PsiAnnotation.class, ExtensibleMembers.class, PsiClass.class),
                processRecordComponent = BaseHandler.class.getMethod("processRecordComponent", PsiRecordComponent.class, Annotation.class, PsiAnnotation.class, ExtensibleMembers.class, PsiClass.class);
        
        static Method specific(final Method method, final PsiElement element) = process.equals(method) ? switch (element) {
            case PsiField _           -> processVariable;
            case PsiMethod _          -> processMethod;
            case PsiClass _           -> processClass;
            case PsiRecordComponent _ -> processRecordComponent;
            default                   -> method;
        } : method;
        
    }
    
    Handler handler;
    
    public BaseHandler() = handler = getClass().getAnnotation(Handler.class);
    
    @Override
    public int compareTo(final BaseHandler<A> other) = (int) (handler().priority() - other.handler().priority());
    
    public boolean derivedFilter(final PsiElement tree) = nonGenerating(tree);
    
    public boolean contextFilter(final PsiClass context) = !(context instanceof PsiCompiledElement);
    
    public final void process(final PsiElement tree, final A annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) {
        if (contextFilter(context)) {
            switch (tree) {
                case PsiField element           -> processVariable(element, annotation, annotationTree, members, context);
                case PsiMethod element          -> processMethod(element, annotation, annotationTree, members, context);
                case PsiClass element           -> processClass(element, annotation, annotationTree, members, context);
                case PsiRecordComponent element -> processRecordComponent(element, annotation, annotationTree, members, context);
                default                         -> throw new AssertionError(STR."Unreachable area: \{tree.getClass()}");
            }
        }
    }
    
    public void processVariable(final PsiField tree, final A annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) { }
    
    public void processMethod(final PsiMethod tree, final A annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) { }
    
    public void processClass(final PsiClass tree, final A annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) { }
    
    public void processRecordComponent(final PsiRecordComponent tree, final A annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) { }
    
    public void collectRelatedTarget(final PsiModifierListOwner tree, final A annotation, final PsiAnnotation annotationTree, final Set<PsiNameIdentifierOwner> targets) { }
    
    public void transformModifiers(final PsiElement tree, final A annotation, final PsiAnnotation annotationTree, final HashSet<String> result) { }
    
    public void transformInterfaces(final PsiClass tree, final A annotation, final PsiAnnotation annotationTree, final HashSet<PsiClass> result) { }
    
    public void transformInterfaceTypes(final PsiClass tree, final A annotation, final PsiAnnotation annotationTree, final HashSet<PsiClassType> result) { }
    
    public void wrapperType(final PsiTypeElement tree, final A annotation, final PsiAnnotation annotationTree, final PsiType result[]) { }
    
    public void check(final PsiElement tree, final A annotation, final PsiAnnotation annotationTree, final ProblemsHolder holder, final QuickFixFactory quickFix) { }
    
    // public void renameAccessorSource(final PsiModifierListOwner tree, final A annotation, final PsiAnnotation annotationTree, final String newName, final Map<PsiElement, String> allRenames, final SearchScope scope) { }
    
    public boolean isImplicitUsage(final PsiElement tree, final ImplicitUsageChecker.RefData refData) = false;
    
    public boolean isImplicitRead(final PsiElement tree, final ImplicitUsageChecker.RefData refData) = false;
    
    public boolean isImplicitWrite(final PsiElement tree, final ImplicitUsageChecker.RefData refData) = false;
    
    public boolean isVariableOut(final PsiVariable variable) = false;
    
    public boolean isSuppressedSpellCheckingFor(final PsiElement element) = false;
    
    public static boolean nonGenerating(final PsiElement tree) {
        if (tree instanceof PsiFieldImpl impl && impl.getNode().findChildByRoleAsPsiElement(ChildRole.NAME) == null)
            return true;
        if (tree instanceof PsiMethodImpl impl && Stream.concat(Stream.of(impl), Stream.of(impl.getParameterList().getParameters())).map(PsiElement::getNode)
                .cast(CompositeElement.class)
                .anyMatch(node -> node.findChildByRoleAsPsiElement(ChildRole.NAME) == null))
            return true;
        return !(tree instanceof PsiNamedElement namedElement && namedElement.getName()?.startsWith("$") ?? false);
    }
    
}
