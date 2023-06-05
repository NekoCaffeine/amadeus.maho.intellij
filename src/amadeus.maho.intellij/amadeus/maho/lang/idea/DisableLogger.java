package amadeus.maho.lang.idea;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.ide.actions.searcheverywhere.WaitForContributorsListenerWrapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.xmlb.BeanBinding;
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsGlobalModelSynchronizerImpl;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Redirect;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.transform.mark.base.Slice;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface DisableLogger {
    
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
    
}
