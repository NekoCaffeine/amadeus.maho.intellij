package amadeus.maho.lang.idea.debugger.render;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StringReference;
import com.sun.jdi.Type;
import com.sun.jdi.Value;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.FullValueEvaluatorProvider;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.SuspendContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.managerThread.SuspendContextCommand;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.ui.tree.render.CompoundRendererProvider;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.debugger.ui.tree.render.ToStringRenderer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.runtime.DebugHelper;

public class CodePathPerceptionRenderer extends CompoundRendererProvider {
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static abstract class ToStringCommand implements SuspendContextCommand {
        
        final EvaluationContext context;
        
        final Value value;
        
        boolean isEvaluated = false;
        
        @Override
        public void action() {
            if (isEvaluated)
                return;
            try {
                final ObjectReference reference = (ObjectReference) value();
                // noinspection SpellCheckingInspection
                final Method codePathStringMethod = DebuggerUtils.findMethod(reference.virtualMachine().classesByName(DebugHelper.CodePathPerception.class.getName())[0], "codePathString", "()Ljava/lang/String;");
                final String valueAsString = DebuggerUtils.processCollectibleValue(
                        () -> context().getDebugProcess().invokeInstanceMethod(context, reference, codePathStringMethod, Collections.emptyList(), 0),
                        result -> result == null ? "null" : result instanceof StringReference stringReference ? stringReference.value() : result.toString()
                );
                evaluationResult(valueAsString);
            } catch (final EvaluateException ex) { evaluationError(ex.getMessage()); }
        }
        
        @Override
        public void commandCancelled() { }
        
        public void setEvaluated() = isEvaluated = true;
        
        @Override
        public SuspendContext getSuspendContext() = context.getSuspendContext();
        
        public abstract void evaluationResult(String message);
        
        public abstract void evaluationError(String message);
        
        public Value getValue() = value;
        
        public EvaluationContext getEvaluationContext() = context;
        
    }
    
    public static final class AutoToStringRenderer extends ToStringRenderer {
        
        { setIsApplicableChecker(type -> DebuggerUtilsAsync.instanceOf(type, DebugHelper.CodePathPerception.class.getName())); }
        
        @Override
        public String getUniqueId() = "CodePathPerceptionAutoToString";
        
        @Override
        public boolean isOnDemand(final EvaluationContext context, final ValueDescriptor descriptor) = false;
        
        @Override
        public boolean isShowValue(final ValueDescriptor valueDescriptor, final EvaluationContext evaluationContext) = true;
        
        @Override
        public String calcLabel(final ValueDescriptor descriptor, final EvaluationContext context, final DescriptorLabelListener listener) throws EvaluateException {
            final Value value = descriptor.getValue();
            context.getDebugProcess().getManagerThread().invokeCommand(new ToStringCommand(context, value) {
                
                @Override
                public void evaluationResult(final String message) {
                    descriptor.setValueLabel(StringUtil.notNullize(message));
                    listener.labelChanged();
                }
                
                @Override
                public void evaluationError(final String message) {
                    // noinspection SpellCheckingInspection
                    final String msg = value != null ? message + " " + JavaDebuggerBundle.message("evaluation.error.cannot.evaluate.tostring", value.type().name()) : message;
                    descriptor.setValueLabelFailed(new EvaluateException(msg, null));
                    listener.labelChanged();
                }
                
            });
            return XDebuggerUIConstants.getCollectingDataMessage();
        }
        
    }
    
    @Override
    protected String getName() = "CodePathPerception";
    
    @Override
    protected boolean isEnabled() = true;
    
    @Override
    protected AutoToStringRenderer getValueLabelRenderer() = { };
    
    @Override
    protected Function<Type, CompletableFuture<Boolean>> getIsApplicableChecker() = type -> DebuggerUtilsAsync.instanceOf(type, DebugHelper.CodePathPerception.class.getName());
    
    @Override
    protected FullValueEvaluatorProvider getFullValueEvaluatorProvider() = (evaluationContext, valueDescriptor) ->
            new JavaValue.JavaFullValueEvaluator(JavaDebuggerBundle.message("message.node.navigate"), evaluationContext) {
                
                @Override
                public void evaluate(final XFullValueEvaluationCallback callback) {
                    try {
                        callback.evaluated("");
                        final Value value = valueDescriptor.getValue();
                        evaluationContext.getDebugProcess().getManagerThread().invokeCommand(new ToStringCommand(evaluationContext, value) {
                            
                            @Override
                            public void evaluationResult(final String message) {
                                final String split[] = message.split("#");
                                if (split.length == 2) {
                                    final Project project = valueDescriptor.getProject();
                                    ReadAction.run(() -> {
                                        final @Nullable PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(split[0], GlobalSearchScope.allScope(project));
                                        if (psiClass != null) {
                                            final @Nullable PsiField field = psiClass.findFieldByName(split[1], false);
                                            if (field != null)
                                                DebuggerUIUtil.invokeLater(() -> field.navigate(true));
                                        }
                                    });
                                }
                            }
                            
                            @Override
                            public void evaluationError(final String message) = DebugHelper.breakpoint();
                            
                        });
                    } catch (final EvaluateException e) { DebugHelper.breakpoint(); }
                }
                
                @Override
                public boolean isShowValuePopup() = false;
                
            };
    
}
