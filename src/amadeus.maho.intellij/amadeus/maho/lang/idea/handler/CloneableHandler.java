package amadeus.maho.lang.idea.handler;

import java.util.HashSet;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.impl.source.PsiImmediateClassType;

import amadeus.maho.lang.Cloneable;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.ExtensibleMembers;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.light.LightMethod;
import amadeus.maho.lang.inspection.Nullable;

import static amadeus.maho.lang.idea.handler.CloneableHandler.PRIORITY;

@Handler(value = Cloneable.class, priority = PRIORITY)
public class CloneableHandler extends BaseHandler<Cloneable> {
    
    public static final int PRIORITY = ConstructorHandler.PRIORITY << 1;
    
    @Override
    public void processClass(final PsiClass tree, final Cloneable annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) {
        if (tree != context)
            return;
        final @Nullable String name = tree.getName();
        if (name == null)
            return;
        final LightMethod clone = { tree, "clone", annotationTree };
        final PsiImmediateClassType type = { tree, PsiSubstitutor.EMPTY };
        clone.setMethodReturnType(type);
        if (members.shouldInject(clone)) {
            clone.setContainingClass(context);
            clone.addModifier(PsiModifier.PUBLIC);
            clone.setNavigationElement(annotationTree);
            clone.setMethodKind(handler().value().getCanonicalName());
            members.inject(clone);
        }
        final LightMethod noArgConstructor = { tree, name, annotationTree };
        noArgConstructor.setConstructor(true);
        if (members.shouldInject(noArgConstructor)) {
            noArgConstructor.setContainingClass(context);
            noArgConstructor.addModifier(PsiModifier.PUBLIC);
            noArgConstructor.setNavigationElement(annotationTree);
            noArgConstructor.setMethodKind(handler().value().getCanonicalName());
            members.inject(noArgConstructor);
        }
        final LightMethod methodTree = { tree, name, annotationTree };
        methodTree.addParameter("source", type);
        methodTree.setConstructor(true);
        if (members.shouldInject(methodTree)) {
            methodTree.setContainingClass(context);
            methodTree.addModifier(PsiModifier.PUBLIC);
            methodTree.setNavigationElement(annotationTree);
            methodTree.setMethodKind(handler().value().getCanonicalName());
            members.inject(methodTree);
        }
        final LightMethod set = { tree, "set", annotationTree };
        set.addParameter("source", type);
        set.setConstructor(true);
        if (members.shouldInject(methodTree)) {
            set.setContainingClass(context);
            set.addModifier(PsiModifier.PUBLIC);
            set.setNavigationElement(annotationTree);
            set.setMethodKind(handler().value().getCanonicalName());
            members.inject(set);
        }
        final LightMethod swap = { tree, "swap", annotationTree };
        swap.addParameter("source", type);
        swap.setConstructor(true);
        if (members.shouldInject(methodTree)) {
            swap.setContainingClass(context);
            swap.addModifier(PsiModifier.PUBLIC);
            swap.setNavigationElement(annotationTree);
            swap.setMethodKind(handler().value().getCanonicalName());
            members.inject(swap);
        }
    }
    
    @Override
    public void transformInterfaces(final PsiClass tree, final Cloneable annotation, final PsiAnnotation annotationTree, final HashSet<PsiClass> result) {
        final @Nullable PsiClass psiClass = JavaPsiFacade.getInstance(tree.getProject()).findClass(java.lang.Cloneable.class.getCanonicalName(), tree.getResolveScope());
        if (psiClass != null)
            result.add(psiClass);
    }
    
    @Override
    public void transformInterfaceTypes(final PsiClass tree, final Cloneable annotation, final PsiAnnotation annotationTree, final HashSet<PsiClassType> result) {
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(tree.getProject());
        final @Nullable PsiClass psiClass = facade.findClass(java.lang.Cloneable.class.getCanonicalName(), tree.getResolveScope());
        if (psiClass != null)
            result.add(facade.getElementFactory().createType(psiClass));
    }
    
}
