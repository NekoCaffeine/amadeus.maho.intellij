package amadeus.maho.lang.idea.debugger.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.objectweb.asm.Type;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.handler.base.marker.BaseMarker;
import amadeus.maho.transform.mark.base.TransformMark;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.vm.JDWP;

@TransformMark(CommandHandler.Marker.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandHandler {
    
    Class<? extends JDWP.IDECommand> value();
    
    @NoArgsConstructor
    class Marker extends BaseMarker<CommandHandler> {
        
        @Getter
        private static final ConcurrentWeakIdentityHashMap<Class<? extends JDWP.IDECommand>, Handler<? extends JDWP.IDECommand>> handlers = { };
        
        @Override
        public synchronized void onMark(final TransformerManager.Context context) {
            final Class<?> clazz = ASMHelper.loadType(Type.getObjectType(sourceClass.name), true, contextClassLoader());
            if (Handler.class.isAssignableFrom(clazz)) {
                handlers()[clazz.getAnnotation(CommandHandler.class).value()] = (Handler<? extends JDWP.IDECommand>) clazz.defaultInstance();
            } else
                throw DebugHelper.breakpointBeforeThrow(new IncompatibleClassChangeError());
        }
        
        @Override
        public boolean advance() = false;
        
    }
    
}
