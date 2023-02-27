package amadeus.maho.lang.idea.debugger;

import java.lang.reflect.Modifier;
import java.util.stream.Stream;

import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ThreadReference;

import com.intellij.debugger.engine.JavaStackFrame;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.impl.XDebugSessionImpl;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.idea.debugger.command.CommandHandler;
import amadeus.maho.lang.idea.debugger.command.Handler;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.runtime.UnsafeHelper;
import amadeus.maho.vm.JDWP;

import static amadeus.maho.lang.idea.debugger.JDIHelper.*;

@TransformProvider
public interface BreakpointReached {
    
    @Hook
    private static void breakpointReached(final XDebugSessionImpl $this, final XBreakpoint<?> breakpoint, final @Nullable String evaluatedLogExpression, final XSuspendContext context, final boolean doProcessing) {
        if (context.getActiveExecutionStack().getTopFrame() instanceof JavaStackFrame stackFrame) {
            final @Nullable Method method = stackFrame.getDescriptor().getMethod();
            if (method != null && method.name().equals("send") && method.declaringType().name().equals(JDWP.MessageQueue.class.getName())) {
                try {
                    if (stackFrame.getStackFrameProxy().getArgumentValues()[0] instanceof ObjectReference reference)
                        if (reference.type() instanceof ReferenceType referenceType) {
                            final Class<?> clazz = Class.forName(referenceType.name());
                            if (JDWP.IDECommand.class.isAssignableFrom(clazz)) {
                                final Handler<JDWP.IDECommand> handler = (Handler<JDWP.IDECommand>) CommandHandler.Marker.handlers()[(Class<? extends JDWP.IDECommand>) clazz];
                                handler.handle((JDWP.IDECommand) projection(stackFrame.getStackFrameProxy().threadProxy().getThreadReference(), reference), $this);
                                $this.resume();
                            }
                        }
                } catch (final EvaluateException | ClassNotFoundException e) { DebugHelper.breakpoint(); }
            }
        }
    }
    
    @SneakyThrows
    private static Object projection(final ThreadReference thread, final ObjectReference reference) {
        final ReferenceType referenceType = reference.referenceType();
        final Class<?> type = Class.forName(referenceType.name());
        if (type.isEnum())
            return type.getEnumConstants()[getEnumOrdinal(thread, reference)];
        final Object instance = UnsafeHelper.allocateInstanceOfType(type);
        Stream.of(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .forEach(field -> {
                    final Object value = value(reference.getValue(referenceType.fieldByName(field.getName())));
                    field.set(instance, value instanceof ObjectReference ref ? projection(thread, ref) : value);
                });
        return instance;
    }
    
}
