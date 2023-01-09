package amadeus.maho.lang.idea.handler.base;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import amadeus.maho.transform.mark.base.TransformMark;

@TransformMark(HandlerMarker.SyntaxMarker.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Syntax {
    
    int priority();
    
}
