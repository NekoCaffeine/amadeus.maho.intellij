package amadeus.maho.lang.idea.handler.base;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CopyOnWriteArrayList;

import org.objectweb.asm.Type;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.handler.base.marker.BaseMarker;
import amadeus.maho.transform.mark.base.TransformMark;
import amadeus.maho.util.bytecode.ASMHelper;

@TransformMark(Handler.Marker.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Handler {
    
    enum Range {
        FIELD, METHOD, CLASS
    }
    
    Class<? extends Annotation> value();
    
    long priority() default 0;
    
    Range[] ranges() default { };
    
    @NoArgsConstructor
    class Marker extends BaseMarker<Handler> {
        
        @Getter
        private static final CopyOnWriteArrayList<BaseHandler<Annotation>> baseHandlers = { };
        
        @Override
        public synchronized void onMark(final TransformerManager.Context context)
                = baseHandlers() += (BaseHandler<Annotation>) ASMHelper.loadType(Type.getObjectType(sourceClass.name), true, contextClassLoader()).defaultInstance();
        
        @Override
        public boolean advance() = false;
        
    }
    
}
