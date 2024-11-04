package amadeus.maho.lang.idea.handler;

import java.lang.annotation.Annotation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

import amadeus.maho.lang.idea.IDEAContext;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Redirect;

public class MethodNameBindingHandler<A extends Annotation> extends BaseHandler<A> {
    
    @Handler(Hook.class)
    public static final class HookHandler extends MethodNameBindingHandler<Hook> { }
    
    @Handler(Redirect.class)
    public static final class RedirectHandler extends MethodNameBindingHandler<Redirect> { }
    
    @Override
    public boolean isSuppressedSpellCheckingFor(final PsiElement element) = IDEAContext.computeReadActionIgnoreDumbMode(() -> element instanceof PsiMethod method && method.hasAnnotation(handler().value().getCanonicalName()));
    
}
