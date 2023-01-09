package amadeus.maho.lang.idea;

import javax.swing.JComponent;

import com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil;
import com.intellij.debugger.impl.InvokeThread;
import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.NoAccessDuringPsiEvents;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.stubs.ObjectStubTree;
import com.intellij.psi.stubs.StubProcessingHelperBase;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.CachedValueStabilityChecker;
import com.intellij.util.IdempotenceChecker;
import com.intellij.util.indexing.ID;

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
    
    @Hook(isStatic = true, value = IdempotenceChecker.class)
    private static <T> Hook.Result checkEquivalence(final @Nullable T existing, final @Nullable T fresh, final Class<?> providerClass, final @Nullable Computable<? extends T> recomputeValue) = Hook.Result.NULL;
    
    @Hook(isStatic = true, value = CachedValueStabilityChecker.class)
    private static <T> Hook.Result checkProvidersEquivalent(final CachedValueProvider<?> p1, final CachedValueProvider<?> p2, final Key<?> key) = Hook.Result.NULL;
    
    @Hook
    private static Hook.Result assertTrue(final Logger $this, final boolean value, final @Nullable Object message) = Hook.Result.TRUE;
    
    @Hook
    private static Hook.Result assertTrue(final Logger $this, final boolean value) = Hook.Result.TRUE;
    
    @Hook
    private static Hook.Result checkOccurred(final LoadingState $this) = Hook.Result.NULL;
    
    @Hook(value = NoAccessDuringPsiEvents.class, isStatic = true)
    private static Hook.Result checkCallContext(final ID<?, ?> indexId) = Hook.Result.NULL;
    
    @Hook(value = NoAccessDuringPsiEvents.class, isStatic = true)
    private static Hook.Result checkCallContext(final String contextDescription) = Hook.Result.NULL;
    
    // e.g. E @NotNull []
    @Hook(value = AnnotationsHighlightUtil.class, isStatic = true)
    private static Hook.Result checkApplicability(final PsiAnnotation annotation, final LanguageLevel level, final PsiFile file)
    = Hook.Result.falseToVoid(!file.isWritable() && annotation.getQualifiedName()?.startsWith("org.jetbrains.annotations.") ?? false, null);
    
    @SneakyThrows
    ConstantLookup ActionToolbarImpl_suppress = new ConstantLookup().recording(it -> it.get(null) instanceof String text && text.startsWith("ActionToolbarImpl.suppress"), ActionToolbarImpl.class);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static @Nullable Object getClientProperty(final @Nullable Object capture, final JComponent $this, final Object key) = capture ?? (ActionToolbarImpl_suppress.constantMapping().containsValue(key) ? Boolean.TRUE : (Object) null);
    
    @Hook(value = InvokeThread.class, isStatic = true)
    private static Hook.Result reportCommandError(final Throwable throwable) = Hook.Result.NULL;
    
    @Hook
    private static Hook.Result inconsistencyDetected(final StubProcessingHelperBase $this, final ObjectStubTree stubTree, final PsiFileWithStubSupport support) {
        (Privilege) $this.onInternalError(support.getVirtualFile());
        return Hook.Result.NULL;
    }
    
}
