package amadeus.maho.lang.idea.handler;

import java.util.stream.Stream;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;

import amadeus.maho.lang.Include;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.ExtensibleMembers;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.light.LightMethod;

import static amadeus.maho.lang.idea.handler.IncludeHandler.PRIORITY;

@Handler(value = Include.class, priority = PRIORITY)
public class IncludeHandler extends BaseHandler<Include> {
    
    public static final int PRIORITY = DelegateHandler.PRIORITY << 2;
    
    @Override
    public boolean contextFilter(final PsiClass context) = true;
    
    @Override
    public void processClass(final PsiClass tree, final Include annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) {
        if (tree != context)
            return;
        annotation.accessPsiClasses(Include::value)
                .map(PsiClassType::resolve)
                .nonnull()
                .map(PsiClass::getAllMethods)
                .flatMap(Stream::of)
                .filter(method -> method.hasModifierProperty(PsiModifier.STATIC))
                .filter(members::shouldInject)
                .map(method -> new LightMethod(context, method, PsiSubstitutor.EMPTY, annotationTree))
                .peek(method -> method.setMethodKind(handler().value().getCanonicalName()))
                .forEach(members::inject);
    }
    
}
