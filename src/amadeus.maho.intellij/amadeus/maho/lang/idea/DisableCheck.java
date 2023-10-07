package amadeus.maho.lang.idea;

import javax.swing.JComponent;

import com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil;
import com.intellij.debugger.impl.InvokeThread;
import com.intellij.diagnostic.LoadingState;
import com.intellij.idea.SystemHealthMonitorKt;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.impl.ActionUpdater;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.NoAccessDuringPsiEvents;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.impl.source.tree.LazyParseableElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.stubs.ObjectStubTree;
import com.intellij.psi.stubs.StubProcessingHelperBase;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.CachedValueStabilityChecker;
import com.intellij.util.IdempotenceChecker;
import com.intellij.util.SlowOperations;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.ID;
import com.intellij.util.ui.EDT;
import com.intellij.vcs.log.data.index.IndexDiagnosticRunner;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.misc.ConstantLookup;

import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function0;

// These checks are too expensive, and most of the time are almost purely redundant calculations
@TransformProvider
interface DisableCheck {
    
    // Maho must be running on the latest OpenJDK
    @Hook(value = SystemHealthMonitorKt.class, isStatic = true, exactMatch = false, forceReturn = true)
    private static void checkRuntime() { } // kt suspend fun
    
    @Hook(value = ThreadingAssertions.class, isStatic = true, exactMatch = false, forceReturn = true)
    private static void throwThreadAccessException() { }
    
    @Hook(exactMatch = false, forceReturn = true)
    private static void runDiagnostic(final IndexDiagnosticRunner $this) { }
    
    @Hook(value = SlowOperations.class, isStatic = true, forceReturn = true)
    private static void assertSlowOperationsAreAllowed() { }
    
    @Hook(value = EDT.class, isStatic = true, forceReturn = true)
    private static void assertIsEdt() { }
    
    @Hook(forceReturn = true)
    private static void assertTextLengthIntact(final LazyParseableElement $this, final CharSequence text, final TreeElement child) { }
    
    @Hook(forceReturn = true)
    private static void setUpHealthCheck(final FileBasedIndexImpl $this) { }
    
    @Hook(isStatic = true, value = IdempotenceChecker.class, forceReturn = true)
    private static <T> void checkEquivalence(final @Nullable T existing, final @Nullable T fresh, final Class<?> providerClass, final @Nullable Computable<? extends T> recomputeValue) { }
    
    @Hook(isStatic = true, value = CachedValueStabilityChecker.class, forceReturn = true)
    private static <T> void checkProvidersEquivalent(final CachedValueProvider<?> p1, final CachedValueProvider<?> p2, final Key<?> key) { }
    
    @Hook(forceReturn = true)
    private static boolean assertTrue(final Logger $this, final boolean value, final @Nullable Object message) = true;
    
    @Hook(forceReturn = true)
    private static boolean assertTrue(final Logger $this, final boolean value) = true;
    
    @Hook(forceReturn = true)
    private static void checkOccurred(final LoadingState $this) { }
    
    @Hook(forceReturn = true)
    private static void assertReadAccessAllowed(final ApplicationImpl $this) { }
    
    @Hook(value = TransactionGuardImpl.class, isStatic = true)
    private static Hook.Result areAssertionsEnabled() = Hook.Result.FALSE;
    
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
    
    @Hook(forceReturn = true)
    private static void inconsistencyDetected(final StubProcessingHelperBase $this, final ObjectStubTree stubTree, final PsiFileWithStubSupport support, final String extraMessage)
            = (Privilege) $this.onInternalError(support.getVirtualFile());
    
    @Hook(forceReturn = true)
    private static <T> T computeOnEdt(final ActionUpdater $this, final Object action, final String operationName, final boolean noRulesInEDT, final Function0<T> call, final Continuation<T> continuation)
            = (T) (Privilege) $this.computeOnEdt(call, continuation);
    
}
