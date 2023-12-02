package amadeus.maho.lang.idea.handler;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifierListOwner;

import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.handler.base.ImplicitUsageChecker;
import amadeus.maho.lang.inspection.Callback;

@Handler(Callback.class)
public class CallbackHandler extends BaseHandler<Callback> {
    
    @Override
    public boolean isImplicitUsage(final PsiElement tree, final ImplicitUsageChecker.RefData refData)
            = tree instanceof PsiModifierListOwner owner && !(tree instanceof PsiLocalVariable) && owner.hasAnnotation(handler().value().getCanonicalName());
    
    @Override
    public boolean isImplicitRead(final PsiElement tree, final ImplicitUsageChecker.RefData refData) = isImplicitUsage(tree, refData);
    
}
