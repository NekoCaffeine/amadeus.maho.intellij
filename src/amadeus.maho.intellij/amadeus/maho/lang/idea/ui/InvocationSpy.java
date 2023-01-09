package amadeus.maho.lang.idea.ui;

import java.awt.event.InvocationEvent;

import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.java.PsiArrayInitializerExpressionImpl;

import amadeus.maho.lang.idea.handler.AssignHandler;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.DebugHelper;

@TransformProvider
public class InvocationSpy {
    
    private static final String enable = "maho.idea.invocation.spy";
    
    public static long time;
    
    @Hook(metadata = @TransformMetadata(enable = enable))
    private static void dispatch_$Pre(final InvocationEvent $this) = time = System.currentTimeMillis();
    
    @Hook(metadata = @TransformMetadata(enable = enable))
    private static void dispatch_$Post(final InvocationEvent $this) {
        if (System.currentTimeMillis() - time > 100)
            DebugHelper.breakpoint();
    }
    
    @Hook
    private static void setTreeParent(final TreeElement $this, final CompositeElement parent) {
        if (parent instanceof PsiArrayInitializerExpressionImpl && $this.getTreeParent() instanceof AssignHandler.PsiArrayInitializerBackNewExpression.ExpressionList)
            DebugHelper.breakpoint();
    }
    
}
