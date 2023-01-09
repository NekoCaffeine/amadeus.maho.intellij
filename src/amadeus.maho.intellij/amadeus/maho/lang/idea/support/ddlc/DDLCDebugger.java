package amadeus.maho.lang.idea.support.ddlc;

import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.diagnostic.Logger;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.DebugHelper;

@TransformProvider
public interface DDLCDebugger {

    @Hook(target = "io.unthrottled.doki.hax.HackComponent", isStatic = true, at = @At(method = @At.MethodInsn(name = "getInstance")), before = false, capture = true, metadata = @TransformMetadata(enable = "ddlc.hack.breakpoint"))
    private static Logger _clinit_(final Logger capture) = new DefaultLogger("") {
        
        @Override
        public boolean isDebugEnabled() = false;
    
        @Override
        public void debug(final String message)  = whenLog();
    
        @Override
        public void debug(final Throwable t)  = whenLog();
    
        @Override
        public void debug(final String message, final Throwable t)  = whenLog();
    
        @Override
        public void info(final String message)  = whenLog();
    
        @Override
        public void info(final String message, final Throwable t)  = whenLog();
    
        @Override
        public void warn(final String message, final Throwable t)  = whenLog();
    
        @Override
        public void error(final String message, final Throwable t, final String... details)  = whenLog();
        
        private void whenLog() = DebugHelper.breakpoint();
        
    };

}
