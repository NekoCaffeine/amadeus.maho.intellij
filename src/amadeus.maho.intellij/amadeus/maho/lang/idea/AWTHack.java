package amadeus.maho.lang.idea;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@SuppressWarnings("removal")
@TransformProvider
public interface AWTHack {
    
    AccessControlContext context = (Privilege) new AccessControlContext(null, true);
    
    @Hook(value = AccessController.class, isStatic = true, forceReturn = true)
    private static AccessControlContext getContext() = context;
    
    @SneakyThrows
    @Hook(value = AccessController.class, isStatic = true, forceReturn = true)
    private static <T> T doPrivileged(final PrivilegedExceptionAction<T> action, final AccessControlContext context) = action.run();
    
    @SneakyThrows
    @Hook(value = AccessController.class, isStatic = true, forceReturn = true)
    private static <T> T doPrivileged(final PrivilegedExceptionAction<T> action) = action.run();
    
}
