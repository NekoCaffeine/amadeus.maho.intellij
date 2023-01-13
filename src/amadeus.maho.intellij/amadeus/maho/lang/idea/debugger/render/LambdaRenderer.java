package amadeus.maho.lang.idea.debugger.render;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.sun.jdi.ClassType;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.Type;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.FullValueEvaluatorProvider;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.MethodBytecodeUtil;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.CompoundRendererProvider;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.debugger.ui.tree.render.ToStringRenderer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;

import amadeus.maho.lang.idea.debugger.JDIHelper;
import amadeus.maho.lang.inspection.Nullable;

public class LambdaRenderer extends CompoundRendererProvider {
    
    public static final class AutoToStringRenderer extends ToStringRenderer {
        
        { setIsApplicableChecker(type -> CompletableFuture.completedFuture(JDIHelper.isLambda(type))); }
        
        @Override
        public String getUniqueId() = "LambdaAutoToString";
        
        @Override
        public boolean isOnDemand(final EvaluationContext context, final ValueDescriptor descriptor) = NodeRendererSettings.getInstance().getToStringRenderer().isOnDemand(context, descriptor);
        
        @Override
        public String calcLabel(final ValueDescriptor descriptor, final EvaluationContext context, final DescriptorLabelListener listener) {
            if (descriptor instanceof ValueDescriptorImpl valueDescriptor && context instanceof EvaluationContextImpl evaluationContext) {
                final @Nullable SourcePosition lambdaPosition = lambdaPosition(evaluationContext, valueDescriptor);
                if (lambdaPosition != null) {
                    final PsiElement at = lambdaPosition.getElementAt();
                    return ReadAction.compute(() -> {
                        final @Nullable PsiMember parent = PsiTreeUtil.getParentOfType(at, PsiMember.class);
                        if (parent != null) {
                            final @Nullable PsiClass containingClass = parent.getContainingClass();
                            if (containingClass != null)
                                return "%s#%s".formatted(containingClass.getQualifiedName(), parent.getName());
                        }
                        return "";
                    });
                }
            }
            return "";
        }
    }
    
    @Override
    protected String getName() = "Lambda";
    
    @Override
    protected boolean isEnabled() = true;
    
    @Override
    protected AutoToStringRenderer getValueLabelRenderer() = { };
    
    @Override
    protected Function<Type, CompletableFuture<Boolean>> getIsApplicableChecker() = type -> CompletableFuture.completedFuture(JDIHelper.isLambda(type));
    
    @Override
    protected FullValueEvaluatorProvider getFullValueEvaluatorProvider() = (evaluationContext, valueDescriptor) ->
            new JavaValue.JavaFullValueEvaluator(JavaDebuggerBundle.message("message.node.navigate"), evaluationContext) {
                
                @Override
                public void evaluate(final XFullValueEvaluationCallback callback) {
                    callback.evaluated("");
                    final @Nullable SourcePosition lambdaPosition = lambdaPosition(evaluationContext, valueDescriptor);
                    if (lambdaPosition != null)
                        DebuggerUIUtil.invokeLater(() -> lambdaPosition.navigate(true));
                }
                
                @Override
                public boolean isShowValuePopup() = false;
                
            };
    
    public static @Nullable SourcePosition lambdaPosition(final EvaluationContextImpl evaluationContext, final ValueDescriptorImpl valueDescriptor) = ReadAction.compute(() -> {
        final DebugProcessImpl debugProcess = evaluationContext.getDebugProcess();
        if (valueDescriptor.getType() instanceof ClassType classType) {
            final @Nullable Method lambdaMethod = MethodBytecodeUtil.getLambdaMethod(classType, debugProcess.getVirtualMachineProxy().getClassesByNameProvider());
            if (lambdaMethod != null) {
                final @Nullable Location lambdaLocation = ContainerUtil.getFirstItem(DebuggerUtilsEx.allLineLocations(lambdaMethod));
                if (lambdaLocation != null) {
                    final @Nullable SourcePosition lambdaPosition = debugProcess.getPositionManager().getSourcePosition(lambdaLocation);
                    return lambdaPosition;
                }
            }
        }
        return null;
    });
    
}
