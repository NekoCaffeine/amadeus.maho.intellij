package amadeus.maho.lang.idea.handler.base;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.ConcurrentSkipListMap;

import org.objectweb.asm.Type;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.handler.base.marker.BaseMarker;
import amadeus.maho.transform.mark.base.TransformMark;
import amadeus.maho.util.bytecode.ASMHelper;

@TransformMark(Syntax.Marker.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Syntax {
    
    int priority();
    
    @NoArgsConstructor
    class Marker extends BaseMarker<Syntax> {
        
        @Getter
        private static final ConcurrentSkipListMap<Integer, BaseSyntaxHandler> syntaxHandlers = { };
        
        @Override
        public synchronized void onMark(final TransformerManager.Context context)
                = syntaxHandlers()[annotation.priority()] = (BaseSyntaxHandler) ASMHelper.loadType(Type.getObjectType(sourceClass.name), true, contextClassLoader()).defaultInstance();
        
        @Override
        public boolean advance() = false;
        
    }
    
}
