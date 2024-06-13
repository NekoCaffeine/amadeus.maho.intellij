package amadeus.maho.lang.idea;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JComponent;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.AutoPopupControllerImpl;
import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.actions.BaseCodeCompletionAction;
import com.intellij.codeInsight.daemon.impl.LibrarySourceNotificationProvider;
import com.intellij.codeInsight.daemon.impl.analysis.GenericsHighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.SwitchBlockHighlightingModel;
import com.intellij.codeInsight.folding.impl.JavaFoldingBuilderBase;
import com.intellij.codeInsight.generation.GenerateEqualsHandler;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.concurrency.JobLauncher;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.ide.actions.CopyTBXReferenceProvider;
import com.intellij.lang.folding.CompositeFoldingBuilder;
import com.intellij.lang.folding.CustomFoldingBuilder;
import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport;
import com.intellij.lang.parameterInfo.ParameterInfoUtils;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiRecordHeader;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiSwitchExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiElementFactoryImpl;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiShortNamesCacheImpl;
import com.intellij.psi.impl.RecordAugmentProvider;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.compiled.ClsElementImpl;
import com.intellij.psi.impl.compiled.ClsModifierListImpl;
import com.intellij.psi.impl.compiled.ClsParsingUtil;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.impl.java.JavaFunctionalExpressionIndex;
import com.intellij.psi.impl.java.stubs.index.JavaShortClassNameIndex;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.psi.impl.search.AllClassesSearchExecutor;
import com.intellij.psi.impl.search.JavaFunctionalExpressionSearcher;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.conflictResolvers.JavaMethodsConflictResolver;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.ui.IconDeferrerImpl;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FileBasedIndexEx;
import com.intellij.util.indexing.UnindexedFilesUpdater;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Fixed;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Redirect;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.Slice;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.runtime.ArrayHelper;

import com.siyeh.ig.BaseInspectionVisitor;
import org.cef.SystemBootstrap;

import static amadeus.maho.util.bytecode.Bytecodes.*;

@TransformProvider
interface Fix {
    
    @Fixed(domain = "JetBrains", shortName = "IDEA-255878", url = "https://youtrack.jetbrains.com/issue/IDEA-255878/UI-thread-deadlock-application-freeze-upon-com.intellij.ui.IconDeferrerImpl.clearCache")
    @Hook
    private static Hook.Result clearCache(final IconDeferrerImpl $this) {
        final Application application = ApplicationManager.getApplication();
        if (application.isDispatchThread())
            return Hook.Result.VOID;
        application.invokeLaterOnWriteThread($this::clearCache);
        return Hook.Result.NULL;
    }
    
    @Hook
    private static Hook.Result addOccurrence(final HighlightUsagesHandlerBase $this, final @Nullable PsiElement element) = Hook.Result.falseToVoid(element == null, null);
    
    // Fixed: Incompatible types. Found: 'O<T>', required: 'O<T>'
    @Hook(value = TypeConversionUtil.class, isStatic = true, at = @At(method = @At.MethodInsn(name = "getType")), before = false, capture = true)
    private static PsiType areTypesAssignmentCompatible(final PsiType capture, final PsiType lType, final PsiExpression rExpr) = GenericsUtil.getVariableTypeByExpressionType(capture);
    
    @Hook
    private static void possiblyInvalidatePhysicalPsi(final FileManagerImpl $this) = ProjectRootManager.getInstance(((Privilege) $this.myManager).getProject()).incModificationCount();
    
    @Hook(value = DebugUtil.class, isStatic = true, forceReturn = true)
    private static @Nullable Object currentInvalidationTrace() = null; // fucking slow
    
    @Hook(value = SwitchBlockHighlightingModel.PatternsInSwitchBlockHighlightingModel.class, isStatic = true, forceReturn = true)
    private static Collection<PsiClass> getPermittedClasses(final PsiClass psiClass) = CachedValuesManager.getProjectPsiDependentCache(psiClass, it -> {
        final @Nullable PsiReferenceList permitsList = it.getPermitsList();
        if (permitsList == null)
            return SyntaxTraverser.psiTraverser(it.getContainingFile())
                    .expandTypes(type -> type != JavaElementType.TYPE_PARAMETER_LIST)
                    .filter(PsiClass.class)
                    .filter(cls -> !(cls instanceof PsiAnonymousClass || PsiUtil.isLocalClass(cls)))
                    .filter(cls -> cls.isInheritor(it, false))
                    .toList();
        return Stream.of(permitsList.getReferencedTypes())
                .map(PsiClassType::resolve)
                .nonnull()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    });
    
    @Redirect(targetClass = RecordAugmentProvider.class, slice = @Slice(@At(method = @At.MethodInsn(name = ASMHelper._INIT_, owner = "com/intellij/psi/impl/light/LightRecordCanonicalConstructor"))))
    private static LightRecordCanonicalConstructor getCanonicalConstructor(final PsiMethod method, final PsiClass containingClass) {
        final PsiRecordComponent components[] = containingClass.getRecordComponents();
        if (components.length > 0 && components[components.length - 1].getType() instanceof PsiEllipsisType)
            return new LightRecordCanonicalConstructor(method, containingClass) {
                @Override
                public boolean isVarArgs() = true;
            };
        return new LightRecordCanonicalConstructor(method, containingClass);
    }
    
    @Hook(value = ClassUtil.class, isStatic = true)
    private static Hook.Result findSubClass(final String name, @Hook.Reference PsiClass parent, final boolean jvmCompatible) {
        if (parent instanceof ClsClassImpl clsClass && clsClass.getNavigationElement() instanceof PsiClass psiClass) {
            parent = psiClass;
            return { };
        }
        return Hook.Result.VOID;
    }
    
    // Remove useless checks
    @Hook
    private static Hook.Result getClassesByName(final PsiShortNamesCacheImpl $this, final String name, final GlobalSearchScope scope) {
        final @Nullable Project project = scope.getProject();
        if (project == null)
            return Hook.Result.VOID;
        return { JavaShortClassNameIndex.getInstance().getClasses(name, project, scope).toArray(PsiClass.ARRAY_FACTORY::create) };
    }
    
    private static ProgressIndicator getOrCreateIndicator() {
        final ProgressIndicator progress = ProgressIndicatorProvider.getGlobalProgressIndicator() ?? new EmptyProgressIndicator();
        progress.setIndeterminate(false);
        return progress;
    }
    
    // Multi-threaded class finding support
    @Hook(value = AllClassesSearchExecutor.class, isStatic = true)
    private static Hook.Result processClassesByNames(final Project project, final GlobalSearchScope scope, final Collection<String> names, final Processor<? super PsiClass> processor) {
        final JavaShortClassNameIndex nameIndex = JavaShortClassNameIndex.getInstance();
        final Map<String, Collection<PsiClass>> resultMap = new ConcurrentHashMap<>();
        JobLauncher.getInstance().invokeConcurrentlyUnderProgress(new ArrayList<>(names), getOrCreateIndicator(), name -> {
            resultMap[name] = nameIndex.getClasses(name, project, scope);
            return true;
        });
        return { names.stream().map(resultMap::get).allMatch(collection -> collection.stream().peek(it -> ProgressIndicatorProvider.checkCanceled()).allMatch(processor::process)) };
    }
    
    @Hook(forceReturn = true)
    private static List<PsiPackageAccessibilityStatement> findExports(final LightJavaModule $this) = List.of(); // slow, unnecessary, see also: AccessibleHandler
    
    @Hook(value = ClsParsingUtil.class, isStatic = true)
    private static Hook.Result isPreviewLevel(final int minor) = Hook.Result.TRUE;
    
    // Fix buggy source code mismatch check
    @Hook
    private static Hook.Result differs(final LibrarySourceNotificationProvider $this, final PsiClass src) = Hook.Result.FALSE;
    
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
    = owner instanceof PsiClassReferenceType type && type.getReference().getParent() instanceof PsiTypeElement &&
      PsiTreeUtil.skipParentsOfType(type.getReference(), PsiTypeElement.class) instanceof PsiMethod method &&
      method.getReturnTypeElement() == PsiTreeUtil.skipMatching(type.getReference(), PsiElement::getParent, it -> !(it.getParent() instanceof PsiMethod))
            ? ArrayHelper.add(capture, PsiAnnotation.TargetType.METHOD) : capture;
    
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
    private static Hook.Result registerError(final BaseInspectionVisitor $this, final PsiElement location, final ProblemHighlightType highlightType, final Object... infos) = Hook.Result.falseToVoid(location.getTextLength() == 0, null);
    
    @Hook(value = JavaFunctionalExpressionSearcher.class, isStatic = true, at = @At(method = @At.MethodInsn(name = "subSequence")), before = false, capture = true)
    private static CharSequence createMemberCopyFromText(final CharSequence capture, final PsiMember member, final TextRange range)
            = member instanceof PsiFieldImpl field && field != (Privilege) field.findFirstFieldInDeclaration() && field.getTypeElement() != null ? STR."\{field.getTypeElement().getText()} \{capture}" : capture;
    
    @Hook(value = JavaFunctionalExpressionSearcher.class, isStatic = true, at = @At(method = @At.MethodInsn(name = "findPsiByAST")), capture = true)
    private static int getNonPhysicalCopy(final int capture, final Map<TextRange, PsiFile> fragmentCache, final JavaFunctionalExpressionIndex.IndexEntry entry, final PsiFunctionalExpression expression) {
        final PsiMember member = PsiTreeUtil.getStubOrPsiParentOfType(expression, PsiMember.class);
        return member instanceof PsiFieldImpl field && field != (Privilege) field.findFirstFieldInDeclaration() && field.getTypeElement() != null ? capture + field.getTypeElement().getText().length() + 1 : capture;
    }
    
    @Hook(value = GenericsHighlightUtil.class, isStatic = true)
    private static Hook.Result hasUnrelatedDefaults(@Hook.Reference List<? extends PsiClass> defaults) {
        defaults = defaults.stream().distinct().toList();
        return { };
    }
    
    @Hook(value = ParameterInfoUtils.class, isStatic = true)
    private static <E extends PsiElement> Hook.Result findArgumentList(final PsiFile file, final int offset, @Hook.Reference int lbraceOffset, final ParameterInfoHandlerWithTabActionSupport findArgumentListHelper, final boolean allowOuter) {
        lbraceOffset = -1;
        return { };
    }
    
    @Hook
    private static void getDisplayName(final ConfigurableWrapper $this) = $this.getConfigurable();
    
    @Hook(value = SystemBootstrap.class, isStatic = true, forceReturn = true)
    private static void loadLibrary(final String libName) {
        try {
            System.loadLibrary(libName);
        } catch (final UnsatisfiedLinkError e) {
            System.load(Path.of(PathManager.getHomePath()) / "jbr" / "bin" / System.mapLibraryName(libName) | "/");
        }
    }
    
    // fucking slow: createImportStaticStatement => reformat
    @Redirect(targetClass = PsiImplUtil.class, selector = "getImplicitStaticImports", slice = @Slice(@At(method = @At.MethodInsn(name = "createImportStaticStatement"))))
    private static PsiImportStaticStatement createImportStaticStatement(final PsiElementFactory factory, final PsiClass owner, final String member) {
        final PsiJavaFile dummy = (Privilege) ((PsiElementFactoryImpl) factory).createDummyJavaFile(STR."import static \{owner.getQualifiedName()}.\{member};");
        return (PsiImportStaticStatement) (Privilege) PsiElementFactoryImpl.extractImport(dummy, true);
    }
    
    @Redirect(targetClass = LightweightHint.class, selector = "getLocationOn", slice = @Slice(@At(method = @At.MethodInsn(name = "isDisposed"))))
    private static boolean isDisposed(final @Nullable JBPopup popup) = popup?.isDisposed() ?? true;
    
    @Hook(forceReturn = true)
    private static boolean sleepIfNeededToGivePriorityToAnotherThread(final CoreProgressManager $this) = false;
    
    // IllegalArgumentException: focusOwner == null
    @Hook(at = @At(var = @At.VarInsn(opcode = ASTORE, var = 3)), capture = true)
    private static JComponent guessBestPopupLocation(final @Nullable JComponent capture, final PopupFactoryImpl $this, final DataContext dataContext)
    = capture ?? (PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext) instanceof IdeFrameImpl frame ? frame.getComponent() : null);
    
    // avoid getQualifiedName crash
    @Hook(forceReturn = true)
    private static void setMirror(final ClsModifierListImpl $this, final TreeElement element) {
        (Privilege) $this.setMirrorCheckingType(element, JavaElementType.MODIFIER_LIST);
        final PsiAnnotation annotations[] = $this.getAnnotations(), mirrorAnnotations[] = SourceTreeToPsiMap.<PsiModifierList>treeToPsiNotNull(element).getAnnotations();
        IDEAContext.runReadActionIgnoreDumbMode(() -> {
            for (final PsiAnnotation annotation : annotations) {
                final @Nullable String qualifiedName = annotation.getQualifiedName();
                if (qualifiedName != null) {
                    final @Nullable PsiAnnotation mirror = ContainerUtil.find(mirrorAnnotations, m -> qualifiedName.equals(m.getQualifiedName()));
                    if (mirror != null)
                        (Privilege) ClsElementImpl.setMirror(annotation, mirror);
                }
            }
        });
    }
    
    // avoid return null when IndexNotReadyException
    @Hook(value = DebuggerUtils.class, isStatic = true, forceReturn = true)
    private static PsiClass findClass(final String className, final Project project, final GlobalSearchScope scope, final boolean fallbackToAllScope) = IDEAContext.computeReadActionIgnoreDumbMode(() -> {
        if ((Privilege) DebuggerUtils.getArrayClass(className) != null)
            return (Privilege) DebuggerUtils.getInstance().createArrayClass(project, LanguageLevelProjectExtension.getInstance(project).getLanguageLevel());
        if (project.isDefault())
            return null;
        final String name = StringUtil.notNullize(StringUtil.substringBefore(className, "<"), className);
        final PsiManager manager = PsiManager.getInstance(project);
        PsiClass psiClass = ClassUtil.findPsiClass(manager, name, null, true, scope);
        if (psiClass == null && fallbackToAllScope) {
            final GlobalSearchScope globalScope = (Privilege) DebuggerUtils.getInstance().getFallbackAllScope(scope, project);
            if (globalScope != null)
                psiClass = ClassUtil.findPsiClass(manager, name, null, true, globalScope);
        }
        return psiClass;
    });
    
}
