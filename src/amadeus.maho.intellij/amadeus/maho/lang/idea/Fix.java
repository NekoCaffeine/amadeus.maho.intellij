package amadeus.maho.lang.idea;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.AutoPopupControllerImpl;
import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.actions.BaseCodeCompletionAction;
import com.intellij.codeInsight.daemon.impl.LibrarySourceNotificationProvider;
import com.intellij.codeInsight.folding.impl.JavaFoldingBuilderBase;
import com.intellij.codeInsight.generation.GenerateEqualsHandler;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.concurrency.JobLauncher;
import com.intellij.configurationStore.schemeManager.SchemeManagerBase;
import com.intellij.diagnostic.IdeaFreezeReporter;
import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.ide.actions.CopyTBXReferenceProvider;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.jna.JnaLoader;
import com.intellij.lang.folding.CompositeFoldingBuilder;
import com.intellij.lang.folding.CustomFoldingBuilder;
import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport;
import com.intellij.lang.parameterInfo.ParameterInfoUtils;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiRecordHeader;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiSwitchExpression;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.PsiShortNamesCacheImpl;
import com.intellij.psi.impl.compiled.ClsParsingUtil;
import com.intellij.psi.impl.java.JavaFunctionalExpressionIndex;
import com.intellij.psi.impl.java.stubs.index.JavaShortClassNameIndex;
import com.intellij.psi.impl.search.AllClassesSearchExecutor;
import com.intellij.psi.impl.search.JavaFunctionalExpressionSearcher;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.conflictResolvers.JavaMethodsConflictResolver;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FileBasedIndexEx;
import com.intellij.util.indexing.UnindexedFilesUpdater;

import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Redirect;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.Slice;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.CallerContext;
import amadeus.maho.util.runtime.ArrayHelper;

import com.siyeh.ig.BaseInspectionVisitor;
import org.cef.SystemBootstrap;

import static org.objectweb.asm.Opcodes.*;

@TransformProvider
interface Fix {
    
    @NoArgsConstructor
    class OpenThread extends Thread { }
    
    @Hook(value = RevealFileAction.class, isStatic = true)
    private static Hook.Result doOpen(final Path dir, final Path toSelect) { // fucking slow, EDT
        if (!(SystemInfo.isWindows && JnaLoader.isLoaded()) || Thread.currentThread() instanceof OpenThread)
            return Hook.Result.VOID;
        new OpenThread(() -> (Privilege) RevealFileAction.doOpen(dir, toSelect)).start();
        return Hook.Result.NULL;
    }
    
    @Redirect(targetClass = RevealFileAction.class, slice = @Slice(@At(method = @At.MethodInsn(name = "error"))))
    private static Hook.Result openViaShellApi(final String message) = Hook.Result.NULL;
    
    @Hook
    private static Hook.Result addOccurrence(final HighlightUsagesHandlerBase $this, final @Nullable PsiElement element) = Hook.Result.falseToVoid(element != null, null);
    
    // Remove useless checks
    @Hook
    private static Hook.Result getClassesByName(final PsiShortNamesCacheImpl $this, final String name, final GlobalSearchScope scope) {
        final @Nullable Project project = scope.getProject();
        if (project == null)
            return Hook.Result.VOID;
        return { JavaShortClassNameIndex.getInstance().get(name, project, scope).toArray(PsiClass.ARRAY_FACTORY::create) };
    }
    
    private static ProgressIndicator getOrCreateIndicator() {
        @Nullable ProgressIndicator progress = ProgressIndicatorProvider.getGlobalProgressIndicator();
        if (progress == null)
            progress = new EmptyProgressIndicator();
        progress.setIndeterminate(false);
        return progress;
    }
    
    // Multi-threaded class finding support
    @Hook(value = AllClassesSearchExecutor.class, isStatic = true)
    private static Hook.Result processClassesByNames(final Project project, final GlobalSearchScope scope, final Collection<String> names, final Processor<? super PsiClass> processor) {
        final JavaShortClassNameIndex nameIndex = JavaShortClassNameIndex.getInstance();
        final Map<String, Collection<PsiClass>> resultMap = new ConcurrentHashMap<>();
        JobLauncher.getInstance().invokeConcurrentlyUnderProgress(new ArrayList<>(names), getOrCreateIndicator(), name -> {
            resultMap[name] = nameIndex.get(name, project, scope);
            return true;
        });
        return {
                names.stream()
                        .map(resultMap::get)
                        .allMatch(collection -> collection.stream()
                        .peek(it -> ProgressIndicatorProvider.checkCanceled())
                        .allMatch(processor::process))
        };
    }
    
    @Hook(value = ClsParsingUtil.class, isStatic = true)
    private static Hook.Result isPreviewLevel(final int minor) = Hook.Result.TRUE;
    
    // Fix buggy source code mismatch check
    @Hook
    private static Hook.Result differs(final LibrarySourceNotificationProvider $this, final PsiClass src) = Hook.Result.FALSE;
    
    AtomicInteger setCurrentCounter = { };
    
    // Fix the color scheme selected by users being overwritten by the default color scheme of the current theme due to inconsistent initialization order
    @Hook
    private static Hook.Result setCurrent(final SchemeManagerBase $this, final Object scheme, final boolean notify, final boolean processChangeSynchronously)
    = Hook.Result.falseToVoid(scheme instanceof EditorColorsScheme && CallerContext.Stack.walker().walk(stream -> stream.anyMatch(frame -> frame.getMethodName().equals("initScheme"))) && setCurrentCounter.getAndIncrement() == 0);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static boolean isDumbAware(final boolean capture, final CustomFoldingBuilder $this) = capture || !($this instanceof JavaFoldingBuilderBase);
    
    @Hook
    private static Hook.Result isDumbAware(final CompositeFoldingBuilder $this) = { ((Privilege) $this.myBuilders).stream().allMatch(DumbService::isDumbAware) };
    
    // Disable smart-ass template generation
    @Hook(value = GenerateEqualsHandler.class, isStatic = true, metadata = @TransformMetadata(disable = "needGenEqAndHash"))
    private static Hook.Result hasNonStaticFields(final PsiClass aClass) = Hook.Result.FALSE;
    
    // <T> @A T, '@A' not applicable to type use
    @Hook(value = AnnotationTargetUtil.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static PsiAnnotation.TargetType[] getTargetsForLocation(final PsiAnnotation.TargetType capture[], final @Nullable PsiAnnotationOwner owner)
    = owner instanceof final PsiClassReferenceType type && type.getReference().getParent() instanceof PsiTypeElement && PsiTreeUtil.skipParentsOfType(type.getReference(), PsiTypeElement.class) instanceof final PsiMethod method &&
      method.getReturnTypeElement() == PsiTreeUtil.skipMatching(type.getReference(), PsiElement::getParent, it -> !(it.getParent() instanceof PsiMethod)) ? ArrayHelper.add(capture, PsiAnnotation.TargetType.METHOD) : capture;
    
    // This eliminates the need to wait for input to stop for a period of time before the auto-complete candidate prompt appears
    @Hook(metadata = @TransformMetadata(disable = "disable.fast.code.completion"))
    private static Hook.Result createHandler(final BaseCodeCompletionAction $this, final CompletionType completionType, @Hook.Reference boolean invokedExplicitly, @Hook.Reference boolean autoPopup, @Hook.Reference boolean synchronous) {
        autoPopup = true;
        invokedExplicitly = false;
        synchronous = true;
        return { };
    }
    
    // Fixed the problem of stupidly disabling the completion when the input is too fast in synchronous completion
    @Hook(at = @At(method = @At.MethodInsn(name = "isPhase")), capture = true, metadata = @TransformMetadata(disable = "disable.fast.code.completion"))
    private static Class<? extends CompletionPhase>[] scheduleAutoPopup(final Class<? extends CompletionPhase>[] capture, final AutoPopupControllerImpl $this, final Editor editor, final CompletionType completionType,
            final @Nullable Condition<? super PsiFile> condition) = ArrayHelper.add(capture, CompletionPhase.ItemsCalculated.class);
    
    // Some annotations(e.g. @Mutable) will generate initialization expression
    @Hook(forceReturn = true)
    private static boolean hasInitializer(final PsiFieldImpl $this) = $this.getInitializer() != null;
    
    @Hook
    @SneakyThrows
    private static <T, E extends Throwable> Hook.Result ignoreDumbMode(final FileBasedIndexEx $this, final DumbModeAccessType dumbModeAccessType, final ThrowableComputable<T, E> computable) {
        if (((Privilege) FileBasedIndexEx.ourDumbModeAccessTypeStack).get().contains(dumbModeAccessType))
            return { computable.compute() };
        return Hook.Result.VOID;
    }
    
    int availableProcessors = Runtime.getRuntime().availableProcessors();
    
    @Hook(value = UnindexedFilesUpdater.class, isStatic = true, forceReturn = true)
    private static int getMaxNumberOfIndexingThreads() = availableProcessors;
    
    @Hook(value = UnindexedFilesUpdater.class, isStatic = true, forceReturn = true)
    private static int getNumberOfScanningThreads() = availableProcessors;
    
    @Hook(value = UnindexedFilesUpdater.class, isStatic = true, forceReturn = true)
    private static int getNumberOfIndexingThreads() = availableProcessors;
    
    @Hook(forceReturn = true)
    private static @Nullable String getQualifiedName(final CopyTBXReferenceProvider $this, final Project project, final List<? extends PsiElement> elements, final @Nullable Editor editor, final DataContext dataContext) = null;
    
    @Hook(at = @At(var = @At.VarInsn(opcode = ASTORE, var = 4)), capture = true)
    private static Hook.Result inferTypeArguments(final Computable<PsiSubstitutor> capture, final MethodCandidateInfo $this, final ParameterTypeInferencePolicy policy, final PsiExpression arguments[], final boolean includeConstraint) {
        final PsiElement myArgumentList = (Privilege) $this.myArgumentList;
        return { !includeConstraint ? myArgumentList == null ? PsiSubstitutor.EMPTY : MethodCandidateInfo.ourOverloadGuard.doPreventingRecursion(myArgumentList, false, capture) ?? capture.get() : capture.get() };
    }
    
    @Hook(forceReturn = true)
    private static @Nullable CandidateInfo resolveConflict(final JavaMethodsConflictResolver $this, final List<CandidateInfo> conflicts) = (Privilege) $this.guardedOverloadResolution(conflicts);
    
    @Hook
    private static Hook.Result createDescription(final RedundantCastInspection $this, final PsiTypeCastExpression cast, final InspectionManager manager, final boolean onTheFly)
            = Hook.Result.falseToVoid(cast.getOperand() instanceof PsiSwitchExpression, null);
    
    @Hook(value = ControlFlowUtil.class, isStatic = true, at = @At(type = @At.TypeInsn(opcode = INSTANCEOF, type = PsiLambdaExpression.class)), capture = true)
    private static Hook.Result findCodeFragment(final PsiElement capture, final PsiElement element) = Hook.Result.falseToVoid(capture instanceof PsiRecordHeader, capture);
    
    @Hook(at = @At(method = @At.MethodInsn(name = "is")), before = false, capture = true)
    private static boolean uiFreezeRecorded(final boolean capture, final IdeaFreezeReporter $this, final long durationMs, final @Nullable File reportDir) = capture && ((Privilege) $this.myDumpTask).getThreadInfos().stream().noneMatch(
            infos -> Stream.of(infos).filter(info -> info.getThreadName().startsWith("AWT-EventQueue")).anyMatch(info -> info.getStackTrace().length > 0 && checkStackFrame(info.getStackTrace()[0])));
    
    private static boolean checkStackFrame(final StackTraceElement element) = element.getClassName().equals("sun.java2d.windows.GDIBlitLoops");
    
    // fixed find in files jrt path
    @Hook(value = FindInProjectUtil.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void setDirectoryName(final FindModel model, final DataContext dataContext) {
        final @Nullable String directoryName = model.getDirectoryName();
        if (directoryName != null) {
            final int index = directoryName.indexOf('!');
            if (index != -1) {
                final Path directory = Path.of(directoryName.substring(0, index));
                if (Files.isDirectory(directory)) {
                    final Path src = directory / "lib" / "src.zip";
                    if (Files.isRegularFile(src))
                        model.setDirectoryName(src + directoryName.substring(index));
                }
            }
        }
    }
    
    // FUCK com.siyeh.ig
    @Hook
    private static Hook.Result registerError(final BaseInspectionVisitor $this, final PsiElement location, final ProblemHighlightType highlightType, final Object... infos)
    = Hook.Result.falseToVoid(location.getTextLength() == 0, null);
    
    @Hook(value = JavaFunctionalExpressionSearcher.class, isStatic = true, at = @At(method = @At.MethodInsn(name = "subSequence")), before = false, capture = true)
    private static CharSequence createMemberCopyFromText(final CharSequence capture, final PsiMember member, final TextRange range)
            = member instanceof final PsiFieldImpl field && field != (Privilege) field.findFirstFieldInDeclaration() && field.getTypeElement() != null ? field.getTypeElement().getText() + " " + capture : capture;
    
    @Hook(value = JavaFunctionalExpressionSearcher.class, isStatic = true, at = @At(method = @At.MethodInsn(name = "findPsiByAST")), capture = true)
    private static int getNonPhysicalCopy(final int capture, final Map<TextRange, PsiFile> fragmentCache, final JavaFunctionalExpressionIndex.IndexEntry entry, final PsiFunctionalExpression expression) {
        final PsiMember member = PsiTreeUtil.getStubOrPsiParentOfType(expression, PsiMember.class);
        return member instanceof final PsiFieldImpl field && field != (Privilege) field.findFirstFieldInDeclaration() && field.getTypeElement() != null ? capture + field.getTypeElement().getText().length() + 1 : capture;
    }
    
    @Hook(value = ParameterInfoUtils.class, isStatic = true)
    private static <E extends PsiElement> Hook.Result findArgumentList(final PsiFile file, final int offset, @Hook.Reference int lbraceOffset,
            final ParameterInfoHandlerWithTabActionSupport findArgumentListHelper, final boolean allowOuter) {
        lbraceOffset = -1;
        return { };
    }
    
    @Hook(value = SystemBootstrap.class, isStatic = true)
    private static void loadLibrary(final String libName) {
        try {
            System.loadLibrary(libName);
        } catch (final UnsatisfiedLinkError e) {
            System.load(Path.of(PathManager.getHomePath()) / "jbr" / "bin" / System.mapLibraryName(libName) | "/");
        }
    }
    
}
