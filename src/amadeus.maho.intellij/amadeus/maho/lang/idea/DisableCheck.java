package amadeus.maho.lang.idea;

import javax.swing.JComponent;

import com.intellij.codeInsight.completion.CompletionAssertions;
import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil;
import com.intellij.debugger.impl.InvokeThread;
import com.intellij.diagnostic.EventWatcher;
import com.intellij.diagnostic.EventWatcherService;
import com.intellij.diagnostic.LoadingState;
import com.intellij.idea.SystemHealthMonitorKt;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.NoAccessDuringPsiEvents;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.impl.source.tree.LazyParseableElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.stubs.ObjectStubTree;
import com.intellij.psi.stubs.StubProcessingHelperBase;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CachedValueStabilityChecker;
import com.intellij.util.IdempotenceChecker;
import com.intellij.util.SlowOperations;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.projectFilter.ProjectIndexableFilesFilterHealthCheck;
import com.intellij.util.ui.EDT;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.misc.ConstantLookup;

// These checks are too expensive, and most of the time are almost purely redundant calculations
@TransformProvider
interface DisableCheck {
    
    // Maho must be running on the latest OpenJDK
    @Hook(value = SystemHealthMonitorKt.class, isStatic = true, exactMatch = false, forceReturn = true)
    private static void checkRuntime() { } // kt suspend fun
    
    @Hook(value = ThreadingAssertions.class, isStatic = true, exactMatch = false, forceReturn = true)
    private static void throwThreadAccessException() { }
    
    @Hook(value = CoreProgressManager.class, isStatic = true, forceReturn = true)
    private static void assertUnderProgress(final ProgressIndicator indicator) { }
    
    @Hook(value = EventWatcherService.class, isStatic = true, forceReturn = true)
    private static EventWatcher[] createWatchersAccordingToConfiguration() = new EventWatcher[0];
    
    @Hook(target = "com.intellij.vcs.log.data.index.IndexDiagnosticRunner", exactMatch = false, forceReturn = true)
    private static void runDiagnostic(final Object $this) { }
    
    @Hook(forceReturn = true)
    private static void launchHealthCheck(final ProjectIndexableFilesFilterHealthCheck $this) { }
    
    @Hook(value = PsiUtil.class, isStatic = true, exactMatch = false, forceReturn = true)
    private static void ensureValidType() { }
    
    @Hook(value = PsiInvalidElementAccessException.class, isStatic = true, forceReturn = true)
    private static boolean isTrackingInvalidation() = false;
    
    @Hook(value = CompletionAssertions.class, isStatic = true, forceReturn = true)
    private static void checkEditorValid(final Editor editor) { }
    
    @Hook(value = CompletionServiceImpl.class, isStatic = true, forceReturn = true)
    private static void assertPhase(final Class<? extends CompletionPhase>... possibilities) { }
    
    @Hook(value = SlowOperations.class, isStatic = true, forceReturn = true)
    private static void assertSlowOperationsAreAllowed() { }
    
    @Hook(value = EDT.class, isStatic = true, forceReturn = true)
    private static void assertIsEdt() { }
    
    @Hook(forceReturn = true)
    private static void assertTextLengthIntact(final LazyParseableElement $this, final CharSequence text, final TreeElement child) { }
    
    @Hook(isStatic = true, value = IdempotenceChecker.class, forceReturn = true)
    private static <T> void checkEquivalence(final @Nullable T existing, final @Nullable T fresh, final Class<?> providerClass, final @Nullable Computable<? extends T> recomputeValue) { }
    
    @Hook(isStatic = true, value = CachedValueStabilityChecker.class, forceReturn = true)
    private static <T> void checkProvidersEquivalent(final CachedValueProvider<?> p1, final CachedValueProvider<?> p2, final Key<?> key) { }
    
    // @Hook(forceReturn = true)
    // private static boolean assertTrue(final Logger $this, final boolean value, final @Nullable Object message) = true;
    //
    // @Hook(forceReturn = true)
    // private static boolean assertTrue(final Logger $this, final boolean value) = true;
    
    @Hook(forceReturn = true)
    private static void checkOccurred(final LoadingState $this) { }
    
    @Hook(forceReturn = true)
    private static void assertReadAccessAllowed(final ApplicationImpl $this) { }
    
    @Hook(value = TransactionGuardImpl.class, isStatic = true, forceReturn = true)
    private static boolean areAssertionsEnabled() = false;
    
    @Hook(value = NoAccessDuringPsiEvents.class, isStatic = true, forceReturn = true)
    private static void checkCallContext(final ID<?, ?> indexId) { }
    
    @Hook(value = NoAccessDuringPsiEvents.class, isStatic = true, forceReturn = true)
    private static void checkCallContext(final String contextDescription) { }
    
    // e.g. E @NotNull []
    @Hook(value = AnnotationsHighlightUtil.class, isStatic = true)
    private static Hook.Result checkApplicability(final PsiAnnotation annotation, final LanguageLevel level, final PsiFile file)
    = Hook.Result.falseToVoid(!file.isWritable() && annotation.getQualifiedName()?.startsWith("org.jetbrains.annotations.") ?? false, null);
    
    @SneakyThrows
    ConstantLookup ActionToolbarImpl_suppress = new ConstantLookup().recording(it -> it.get(null) instanceof String text && text.startsWith("ActionToolbarImpl.suppress"), ActionToolbarImpl.class);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static @Nullable Object getClientProperty(final @Nullable Object capture, final JComponent $this, final Object key) = capture ?? (ActionToolbarImpl_suppress.constantMapping().containsValue(key) ? Boolean.TRUE : (Object) null);
    
    @Hook(value = InvokeThread.class, isStatic = true, forceReturn = true)
    private static void reportCommandError(final Throwable throwable) { }
    
    @Hook(forceReturn = true, exactMatch = false)
    private static void inconsistencyDetected(final StubProcessingHelperBase $this, final ObjectStubTree stubTree, final PsiFileWithStubSupport support, final String extraMessage)
            = (Privilege) $this.onInternalError(support.getVirtualFile());
    
}
