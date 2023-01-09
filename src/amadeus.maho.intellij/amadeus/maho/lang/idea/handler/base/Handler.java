package amadeus.maho.lang.idea.handler.base;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import amadeus.maho.transform.mark.base.TransformMark;

@TransformMark(HandlerMarker.Marker.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Handler {
    
    enum Range {
        FIELD, METHOD, CLASS
    }
    
    Class<? extends Annotation> value();
    
    long priority() default 0;
    
    Range[] ranges() default { };
    
}
