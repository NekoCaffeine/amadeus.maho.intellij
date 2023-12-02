package amadeus.maho.lang.idea.handler;

import java.lang.annotation.Annotation;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;

import amadeus.maho.lang.ResourceAgent;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.handler.base.ImplicitUsageChecker;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Proxy;
import amadeus.maho.transform.mark.Redirect;
import amadeus.maho.transform.mark.TransformTarget;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.event.Listener;

public class IndirectAgreementHandler<A extends Annotation> extends BaseHandler<A> {
    
    @Handler(TransformProvider.class)
    public static final class TransformProviderHandler extends IndirectAgreementHandler<TransformProvider> {
        
        @Override
        public boolean isImplicitUsage(final PsiElement tree, final ImplicitUsageChecker.RefData refData)
                = tree instanceof PsiClass clazz && clazz.hasAnnotation(handler().value().getCanonicalName());
        
    }
    
    @Handler(Hook.class)
    public static final class HookHandler extends IndirectAgreementHandler<Hook> { }
    
    @Handler(Redirect.class)
    public static final class RedirectHandler extends IndirectAgreementHandler<Redirect> { }
    
    @Handler(Proxy.class)
    public static final class ProxyHandler extends IndirectAgreementHandler<Proxy> { }
    
    @Handler(TransformTarget.class)
    public static final class TransformTargetHandler extends IndirectAgreementHandler<TransformTarget> { }
    
    @Handler(ResourceAgent.class)
    public static final class ResourceAgentHandler extends IndirectAgreementHandler<ResourceAgent> { }
    
    @Handler(Listener.class)
    public static final class ListenerHandler extends IndirectAgreementHandler<Listener> { }
    
    @Override
    public boolean isImplicitUsage(final PsiElement tree, final ImplicitUsageChecker.RefData refData)
            = tree instanceof PsiMethod method && method.hasAnnotation(handler().value().getCanonicalName()) || isImplicitRead(tree, refData);
    
    @Override
    public boolean isImplicitRead(final PsiElement tree, final ImplicitUsageChecker.RefData refData)
            = !(tree instanceof PsiLocalVariable) && PsiTreeUtil.getContextOfType(tree, PsiMethod.class)?.hasAnnotation(handler().value().getCanonicalName()) ?? false;
    
}
