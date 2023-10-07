package amadeus.maho.lang.idea;

import java.util.ResourceBundle;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.ide.actions.searcheverywhere.WaitForContributorsListenerWrapper;
import com.intellij.ide.ui.UIThemeBeanKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.xmlb.BeanBinding;
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsGlobalModelSynchronizerImpl;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Redirect;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.transform.mark.base.Slice;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface DisableLogger {
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class FakeLogger implements System.Logger {
        
        System.Logger logger;
        
        @Override
        public String getName() = logger.getName();
        
        @Override
        public boolean isLoggable(final Level level) = false;
        
        @Override
        public void log(final Level level, final ResourceBundle bundle, final String msg, final Throwable thrown) { }
        
        @Override
        public void log(final Level level, final ResourceBundle bundle, final String format, final Object... params) { }
        
    }
    
    @Hook(value = System.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static System.Logger getLogger(final System.Logger capture, final String name) {
        if (name.startsWith("com.github.benmanes."))
            return new FakeLogger(capture);
        return capture;
    }
    
    @Redirect(targetClass = JpsGlobalModelSynchronizerImpl.class, selector = At.Lookup.WILDCARD, slice = @Slice(@At(method = @At.MethodInsn(name = "info"))))
    private static void info_$JpsGlobalModelSynchronizerImpl(final Logger logger, final String msg) { }
    
    @Redirect(targetClass = AbstractPopup.class, selector = "show", slice = @Slice(@At(method = @At.MethodInsn(name = "warn"))))
    private static void warn_$AbstractPopup(final Logger logger, final String msg) { }
    
    @Hook(value = WaitForContributorsListenerWrapper.class, forceReturn = true, exactMatch = false)
    private static void logNonFinished() { }
    
    @Hook(target = "org.jetbrains.java.decompiler.IdeaLogger", forceReturn = true, exactMatch = false)
    private static void writeMessage() { }
    
    @Redirect(targetClass = JavaDocInfoGenerator.class, selector = "getDocComment", slice = @Slice(@At(method = @At.MethodInsn(name = "info"))))
    private static void info_$JavaDocInfoGenerator(final Logger logger, final String msg) { }
    
    @Redirect(targetClass = AnalysisScope.class, selector = "createFilesSet", slice = @Slice(@At(method = @At.MethodInsn(name = "info"))))
    private static void info_$AnalysisScope(final Logger logger, final String msg) { }
    
    @Redirect(target = "com.jediterm.terminal.model.TerminalTextBuffer", selector = "getLine", slice = @Slice(@At(method = @At.MethodInsn(name = "error"))))
    private static void error_$TerminalTextBuffer(final @InvisibleType("org.slf4j.Logger") Object logger, final String msg) { }
    
    @Redirect(target = "com.intellij.featureStatistics.fusCollectors.WSLInstallationsCollector", selector = "getMetrics", slice = @Slice(@At(method = @At.MethodInsn(name = "warn"))))
    private static void warn_$TerminalTextBuffer(final Logger logger, final String msg) { }
    
    @Hook(value = BeanBinding.class, isStatic = true, forceReturn = true)
    private static boolean isAssertBindings(final Class<?> owner) = true;
    
    @Redirect(targetClass = UIThemeBeanKt.class, selector = "readTheme", slice = @Slice(@At(method = @At.MethodInsn(name = "warn"))))
    private static void warn_$UIThemeBeanKt(final Logger logger, final String msg) { }
    
}
