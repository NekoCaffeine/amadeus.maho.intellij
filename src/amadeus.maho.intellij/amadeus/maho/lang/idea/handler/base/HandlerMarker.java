package amadeus.maho.lang.idea.handler.base;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.RefCountHolder;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.canBeFinal.CanBeFinalHandler;
import com.intellij.ide.structureView.StructureViewExtension;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceWrapper;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.ResolveState;
import com.intellij.psi.TypeAnnotationProvider;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.impl.DiffLog;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.search.MethodUsagesSearcher;
import com.intellij.psi.impl.source.ClassInnerStuffCache;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.impl.source.tree.LazyParseableElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.impl.source.tree.java.ClassElement;
import com.intellij.psi.impl.source.tree.java.JavaFileElement;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.stubs.LightStubBuilder;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameJavaMethodProcessor;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.RelatedUsageInfo;
import com.intellij.spellchecker.JavaSpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.impl.rules.JavaUsageTypeProvider;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.MultiMap;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.idea.IDEAContext;
import amadeus.maho.lang.idea.handler.AccessibleHandler;
import amadeus.maho.lang.idea.light.LightBridgeMethod;
import amadeus.maho.lang.idea.light.LightElement;
import amadeus.maho.lang.idea.light.LightElementReference;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Redirect;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.Slice;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.dynamic.InvokeContext;
import amadeus.maho.util.function.Consumer4;
import amadeus.maho.util.runtime.ObjectHelper;
import amadeus.maho.util.tuple.Tuple2;

import static amadeus.maho.util.bytecode.Bytecodes.ATHROW;

public class HandlerMarker {
    
    @TransformProvider
    public static class ReferenceSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
        
        @Getter
        @RequiredArgsConstructor
        @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
        public static class PsiMemberReference<T extends PsiNameIdentifierOwner> extends PsiReferenceBase.Immediate<T> {
            
            String name;
            
            @Override
            protected TextRange calculateDefaultRangeInElement() = getElement().getNameIdentifier()?.getTextRangeInParent() ?? super.calculateDefaultRangeInElement();
            
            @Override
            public PsiElement handleElementRename(final String newElementName) throws IncorrectOperationException = getElement().setName(remapName(newElementName, getElement(), name));
            
        }
        
        public ReferenceSearcher() = super(true);
        
        public static Collection<PsiNameIdentifierOwner> relatedTargets(final PsiElement element) = CachedValuesManager.getProjectPsiDependentCache(element, _ -> new ConcurrentLinkedQueue<>());
        
        @Override
        public void processQuery(final ReferencesSearch.SearchParameters parameters, final Processor<? super PsiReference> consumer)
                = search(parameters.getElementToSearch(), parameters.getScopeDeterminedByUser(), parameters.getOptimizer(), consumer);
        
        private static void search(final PsiElement elementToSearch, final SearchScope scope, final SearchRequestCollector optimizer, final Processor<? super PsiReference> consumer) {
            if (elementToSearch instanceof final PsiModifierListOwner owner && owner instanceof final PsiNamedElement namedElement) {
                final @Nullable String name = namedElement.getName();
                if (name != null) {
                    final HashSet<PsiNameIdentifierOwner> targets = { };
                    EntryPoint.process(owner, (handler, target, annotation, annotationTree) -> handler.collectRelatedTarget(owner, annotation, annotationTree, targets));
                    targets *= relatedTargets(owner);
                    targets -= null;
                    targets.forEach(target -> {
                        if (target.getNameIdentifier() != null)
                            consumer.process(new PsiMemberReference<>(target, target, name));
                        if (target instanceof final PsiMethod method)
                            OverridingMethodsSearch.search(method).forEach((Consumer<? super PsiMethod>) impl -> {
                                if (impl.getNameIdentifier() != null)
                                    consumer.process(new PsiMemberReference<>(impl, impl, name));
                            });
                    });
                    targets.forEach(target -> ReferencesSearch.searchOptimized(target, scope.intersectWith(PsiSearchHelper.getInstance(target.getProject()).getUseScope(target)), false, optimizer,
                            reference -> consumer.process(new PsiReferenceWrapper(reference) {
                                
                                @Override
                                public PsiElement handleElementRename(final String newElementName) throws IncorrectOperationException
                                        = super.handleElementRename(reference.resolve() instanceof final PsiNamedElement named ? remapName(newElementName, named, name) : newElementName);
                                
                            })));
                }
            }
        }
        
        private static String remapName(final String newElementName, final PsiNamedElement target, final String name) {
            final @Nullable String targetName = target.getName();
            if (targetName != null && targetName.length() > name.length() && targetName.contains(name))
                return targetName.replaceFirst(name, newElementName);
            return newElementName;
        }
        
        @Hook
        private static void processQuery(final MethodUsagesSearcher $this, final MethodReferencesSearch.SearchParameters parameters, final Processor<PsiReference> consumer) = IDEAContext.runReadActionIgnoreDumbMode(() ->
                search(parameters.getMethod(), parameters.getEffectiveSearchScope(), parameters.getOptimizer(), consumer));
        
        @Hook
        private static Hook.Result renameElement(final RenameJavaMethodProcessor $this, final PsiElement element, final String newName, @Hook.Reference UsageInfo usages[], final RefactoringElementListener listener) {
            final PsiMethod method = (PsiMethod) element;
            final String name = method.getName();
            usages = Stream.of(usages).filter(usage -> {
                if (usage.getElement() instanceof final PsiMethod usageMethod && !name.equals(usageMethod.getName())) {
                    usageMethod.setName(remapName(newName, usageMethod, name));
                    return false;
                }
                return true;
            }).toArray(UsageInfo[]::new);
            return { };
        }
        
    }
    
    @TransformProvider
    public interface UsageTypeProvider {
        
        UsageType
                associationMethod   = { () -> "Association method" },
                operatorOverloading = { () -> "Operator Overloading" };
        
        @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
        private static @Nullable UsageType getUsageType(final @Nullable UsageType capture, final JavaUsageTypeProvider $this, final PsiElement element, final UsageTarget targets[])
                = capture ?? switch (element) {
            case PsiMethod method   -> associationMethod;
            case PsiJavaToken token -> operatorOverloading;
            default                 -> null;
        };
        
    }
    
    @TransformProvider
    public static class InplaceRenameHandler extends VariableInplaceRenameHandler {
        
        @NoArgsConstructor
        public static class MemberInplaceRenamerEx extends MemberInplaceRenamer {
            
            @Override
            protected boolean acceptReference(final PsiReference reference) {
                final @Nullable String name = myElementToRename.getName();
                return name != null && reference.getCanonicalText().contains(name);
            }
            
            @Override
            protected PsiElement getNameIdentifier() {
                final PsiFile currentFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
                if (currentFile == myElementToRename.getContainingFile())
                    return super.getNameIdentifier();
                if (currentFile != null) {
                    int offset = myEditor.getCaretModel().getOffset();
                    offset = TargetElementUtil.adjustOffset(currentFile, myEditor.getDocument(), offset);
                    final PsiElement elementAt = currentFile.findElementAt(offset);
                    if (elementAt != null) {
                        final PsiElement referenceExpression = elementAt.getParent();
                        if (referenceExpression != null) {
                            final PsiReference reference = referenceExpression.getReference();
                            if (reference != null) {
                                final @Nullable PsiElement resolve = reference.resolve();
                                if (resolve == myElementToRename || resolve != null && resolve.isEquivalentTo(myElementToRename))
                                    return elementAt;
                            }
                        }
                    }
                    return null;
                }
                return null;
            }
            
            @Override
            protected @Nullable PsiElement getSelectedInEditorElement(final @Nullable PsiElement nameIdentifier, final Collection<? extends PsiReference> refs,
                    final Collection<? extends Pair<PsiElement, TextRange>> stringUsages, final int offset) {
                if (nameIdentifier != null) {
                    final TextRange range = nameIdentifier.getTextRange();
                    if (range != null && checkRangeContainsOffset(offset, range, nameIdentifier, 0))
                        return nameIdentifier;
                }
                for (final PsiReference ref : refs) {
                    final PsiElement element = ref.getElement();
                    if (checkRangeContainsOffset(offset, ref.getRangeInElement(), element))
                        return element;
                }
                for (final Pair<PsiElement, TextRange> stringUsage : stringUsages)
                    if (checkRangeContainsOffset(offset, stringUsage.second, stringUsage.first))
                        return stringUsage.first;
                return nameIdentifier?.getContainingFile()?.findElementAt(offset) ?? null;
            }
            
            protected boolean checkRangeContainsOffset(final int offset, final TextRange textRange, final PsiElement element) = checkRangeContainsOffset(offset, textRange, element, element.getTextRange().getStartOffset());
            
            protected boolean checkRangeContainsOffset(final int offset, final TextRange textRange, final PsiElement element, final int shiftOffset) {
                final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(myProject);
                final PsiLanguageInjectionHost injectionHost = injectedLanguageManager.getInjectionHost(element);
                if (injectionHost != null) {
                    final PsiElement nameIdentifier = getNameIdentifier();
                    final PsiLanguageInjectionHost initialInjectedHost = nameIdentifier != null ? injectedLanguageManager.getInjectionHost(nameIdentifier) : null;
                    if (initialInjectedHost != null && initialInjectedHost != injectionHost)
                        return false;
                    return injectedLanguageManager.injectedToHost(element, textRange).shiftRight(shiftOffset).containsOffset(offset);
                }
                return textRange.shiftRight(shiftOffset).containsOffset(offset);
            }
            
            @Override
            protected TextRange getRangeToRename(final PsiReference reference) {
                final TextRange range = super.getRangeToRename(reference);
                final PsiElement resolved = reference.resolve();
                if (resolved instanceof final PsiNamedElement namedElement) {
                    final @Nullable String
                            name = namedElement.getName(),
                            toRename = myElementToRename.getName();
                    if (name != null && toRename != null) {
                        final int offset = range.getStartOffset() + name.indexOf(toRename);
                        return { offset, offset + toRename.length() };
                    }
                }
                return range;
            }
            
            @Override
            protected MemberInplaceRenamerEx createInplaceRenamerToRestart(final PsiNamedElement variable, final Editor editor, final String initialName) = { variable, getSubstituted(), editor, initialName, myOldName };
            
        }
        
        @Hook
        public static Hook.Result isAvailableOnDataContext(final VariableInplaceRenameHandler $this, final DataContext dataContext) = checkDataContext(dataContext);
        
        @Hook
        public static Hook.Result isAvailableOnDataContext(final PsiElementRenameHandler $this, final DataContext dataContext) = checkDataContext(dataContext);
        
        public static Hook.Result checkDataContext(final DataContext dataContext) {
            final @Nullable Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
            final @Nullable PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
            if (editor != null && file != null) {
                final @Nullable PsiReference at = file.findReferenceAt(editor.getCaretModel().getOffset());
                if (checkReference(at) || at instanceof final PsiMultiReference reference && Stream.of(reference.getReferences()).anyMatch(InplaceRenameHandler::checkReference))
                    return Hook.Result.FALSE;
            }
            return Hook.Result.VOID;
        }
        
        public static boolean checkReference(final @Nullable PsiReference at) = at instanceof LightElementReference || at instanceof final PsiJavaCodeReferenceElement reference && reference.getText().equals("self");
        
        @Override
        protected boolean isAvailable(PsiElement element, final Editor editor, final PsiFile file) {
            PsiElement nameSuggestionContext = file.findElementAt(editor.getCaretModel().getOffset());
            if (nameSuggestionContext == null && editor.getCaretModel().getOffset() > 0)
                nameSuggestionContext = file.findElementAt(editor.getCaretModel().getOffset() - 1);
            if (element == null && LookupManager.getActiveLookup(editor) != null)
                element = PsiTreeUtil.getParentOfType(nameSuggestionContext, PsiNamedElement.class);
            final RefactoringSupportProvider supportProvider = element == null ? null : LanguageRefactoringSupport.INSTANCE.forContext(element);
            return editor.getSettings().isVariableInplaceRenameEnabled() && supportProvider != null &&
                   element instanceof PsiNameIdentifierOwner && supportProvider.isMemberInplaceRenameAvailable(element, nameSuggestionContext);
        }
        
        @Override
        public void invoke(final Project project, final @Nullable Editor editor, final PsiFile file, final DataContext dataContext) {
            if (editor != null) {
                PsiElement element = PsiElementRenameHandler.getElement(dataContext);
                if (element == null)
                    element = BaseRefactoringAction.getElementAtCaret(editor, file);
                if (element != null) {
                    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                    final @Nullable PsiElement target = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, editor);
                    if (target != null)
                        PsiElementRenameHandler.invoke(target, project, file.getViewProvider().findElementAt(editor.getCaretModel().getOffset()), editor);
                }
            } else
                super.invoke(project, editor, file, dataContext);
        }
        
        @Override
        public @Nullable InplaceRefactoring doRename(final PsiElement element, final Editor editor, @Nullable final DataContext dataContext)
                = doRenameInplace((PsiNameIdentifierOwner) element, (PsiNameIdentifierOwner) RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, editor), editor, dataContext); // FIXME PsiIdentifierImpl
        
        public @Nullable InplaceRefactoring doRenameInplace(final PsiNameIdentifierOwner source, final @Nullable PsiNameIdentifierOwner elementToRename, final Editor editor, @Nullable final DataContext dataContext) {
            if (dataContext == null || elementToRename == null)
                return null;
            final RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(elementToRename);
            if (processor.isInplaceRenameSupported()) {
                final StartMarkAction startMarkAction = StartMarkAction.canStart(editor);
                if (startMarkAction == null || processor.substituteElementToRename(elementToRename, editor) == elementToRename) {
                    // final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext); // cannot share data context between Swing events, out of action
                    final PsiFile file = source.getManager().findFile(FileDocumentManager.getInstance().getFile(editor.getDocument()));
                    final CaretModel caretModel = editor.getCaretModel();
                    int caretOffset = caretModel.getOffset();
                    @Nullable PsiReference at = file.findReferenceAt(caretOffset);
                    @Nullable PsiElement resolve = at == null ? null : at.resolve();
                    if (!source.equals(resolve)) {
                        at = file.findReferenceAt(--caretOffset);
                        if (at != null)
                            resolve = at.resolve();
                    }
                    if (source.equals(resolve)) {
                        final int offset = resolve instanceof PsiReferenceExpression ? -1 : file.findElementAt(caretOffset).getTextOffset();
                        if (offset != -1) {
                            final @Nullable String text = elementToRename.getName();
                            if (text != null && source.getName() != null) {
                                final int start = offset + source.getName().indexOf(text), end = start + text.length();
                                if (caretOffset < start)
                                    caretModel.moveToOffset(start);
                                else if (caretOffset > end)
                                    caretModel.moveToOffset(end);
                            }
                        }
                    }
                    processor.substituteElementToRename(elementToRename, editor, new Pass<>() {
                        @Override
                        public void pass(final PsiElement element) {
                            final MemberInplaceRenamer renamer = createMemberRenamer(element, elementToRename, editor);
                            if (!createMemberRenamer(element, elementToRename, editor).performInplaceRename())
                                performDialogRename(elementToRename, editor, dataContext, renamer.getInitialName());
                        }
                    });
                    return null;
                } else {
                    final InplaceRefactoring inplaceRefactoring = editor.getUserData(InplaceRefactoring.INPLACE_RENAMER);
                    if (inplaceRefactoring != null && inplaceRefactoring.getClass() == MemberInplaceRenamerEx.class) {
                        final TemplateState templateState = TemplateManagerImpl.getTemplateState(InjectedLanguageEditorUtil.getTopLevelEditor(editor));
                        if (templateState != null)
                            templateState.gotoEnd(true);
                    }
                }
            }
            performDialogRename(elementToRename, editor, dataContext, null);
            return null;
        }
        
        protected MemberInplaceRenamerEx createMemberRenamer(final PsiElement element, final PsiNameIdentifierOwner elementToRename, final Editor editor)
                = { elementToRename, elementToRename, editor, elementToRename.getName(), elementToRename.getName() };
        
    }
    
    @TransformProvider
    public static class RenameLightElementProcessor extends RenamePsiElementProcessor {
        
        public boolean canProcessElement(final PsiElement element) = element instanceof LightElement && !(element.getNavigationElement() instanceof PsiAnnotation);
        
        public @Nullable PsiElement substituteElementToRename(final PsiElement element, final @Nullable Editor editor) = element.getNavigationElement();
        
        @Hook(value = RenameProcessor.class, isStatic = true)
        public static Hook.Result classifyUsages(final Collection<? extends PsiElement> elements, final Collection<UsageInfo> usages) {
            final MultiMap<PsiElement, UsageInfo> result = { };
            for (final UsageInfo usage : usages) {
                if (usage.getReference() instanceof com.intellij.psi.impl.light.LightElement)
                    continue; //filter out implicit references (e.g. from derived class to super class' default constructor)
                final MoveRenameUsageInfo usageInfo = (MoveRenameUsageInfo) usage;
                if (usage instanceof final RelatedUsageInfo relatedUsageInfo) {
                    final PsiElement relatedElement = relatedUsageInfo.getRelatedElement();
                    if (elements.contains(relatedElement))
                        result.putValue(relatedElement, usage);
                } else {
                    final @Nullable PsiReference reference = usageInfo.getReference();
                    final @Nullable PsiElement resolve = reference == null ? null : reference.resolve();
                    if (resolve != null && elements.contains(resolve))
                        result.putValue(resolve, usage);
                    else {
                        final PsiElement referenced = usageInfo.getReferencedElement();
                        if (elements.contains(referenced))
                            result.putValue(referenced, usage);
                        else if (referenced != null) {
                            final PsiElement indirect = referenced.getNavigationElement();
                            if (elements.contains(indirect))
                                result.putValue(indirect, usage);
                        }
                    }
                }
            }
            return { result };
        }
        
    }
    
    // Used to display the injected dummy symbols in the structure UI
    public static class StructureInjecter implements StructureViewExtension {
        
        @Override
        public Class<? extends PsiElement> getType() = PsiClass.class;
        
        @Override
        public StructureViewTreeElement[] getChildren(final PsiElement parent)
                = Stream.concat(Stream.concat(Stream.of(((PsiClass) parent).getInnerClasses()), Stream.of(((PsiClass) parent).getMethods())), Stream.of(((PsiClass) parent).getFields()))
                .filter(LightElement.class::isInstance)
                .map(element -> (PsiElement & LightElement) element)
                .map(StructureElement::new)
                .toArray(StructureViewTreeElement[]::new);
        
        @Override
        public @Nullable Object getCurrentEditorElement(final Editor editor, final PsiElement element) = null;
        
    }
    
    @TransformProvider
    public static class ImplicitUsageChecker extends CanBeFinalHandler implements ImplicitUsageProvider {
        
        @RequiredArgsConstructor
        @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
        public static class RefData {
            
            RefCountHolder inner;
            
            public MultiMap<PsiElement, PsiReference> localRefMap() = (Privilege) inner.myLocalRefsMap;
            
        }
        
        private static @Nullable RefData refData(final PsiElement element) {
            @Nullable PsiFile file = PsiTreeUtil.getParentOfType(element, PsiFile.class);
            if (file == null && element instanceof final LightElement lightElement)
                file = lightElement.equivalents().stream()
                        .map(it -> PsiTreeUtil.getParentOfType(it, PsiFile.class))
                        .nonnull()
                        .findFirst()
                        .orElse(null);
            if (file == null)
                return null;
            final Project project = element.getProject();
            final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
            final @Nullable TextRange dirtyScope = document == null ? null : DaemonCodeAnalyzerEx.getInstanceEx(project).getFileStatusMap().getFileDirtyScope(document, file, com.intellij.codeHighlighting.Pass.UPDATE_ALL);
            return { (Privilege) RefCountHolder.get(file, dirtyScope ?? file.getTextRange()) };
        }
        
        @Override
        public boolean isImplicitUsage(final PsiElement element) {
            final @Nullable RefData refData = refData(element);
            return refData != null && (
                    Handler.Marker.baseHandlers().stream().anyMatch(handler -> handler.isImplicitUsage(element, refData) || handler.isImplicitRead(element, refData)) ||
                    Syntax.Marker.syntaxHandlers().values().stream().anyMatch(handler -> handler.isImplicitUsage(element, refData) || handler.isImplicitRead(element, refData))
            );
        }
        
        @Override
        public boolean isImplicitRead(final PsiElement element) {
            final @Nullable RefData helper = refData(element);
            return helper != null && (
                    Handler.Marker.baseHandlers().stream().anyMatch(handler -> handler.isImplicitRead(element, helper)) ||
                    Syntax.Marker.syntaxHandlers().values().stream().anyMatch(handler -> handler.isImplicitRead(element, helper))
            );
        }
        
        @Override
        public boolean isImplicitWrite(final PsiElement element) {
            final @Nullable RefData helper = refData(element);
            return helper != null && (
                    Handler.Marker.baseHandlers().stream().anyMatch(handler -> handler.isImplicitWrite(element, helper)) ||
                    Syntax.Marker.syntaxHandlers().values().stream().anyMatch(handler -> handler.isImplicitWrite(element, helper))
            );
        }
        
        @Override
        public boolean canBeFinal(final PsiMember member) = !isImplicitWrite(member);
        
        @Hook(value = DefUseUtil.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
        public static void getUnusedDefs(final @Nullable List<DefUseUtil.Info> capture, final PsiElement body, final Set<? super PsiVariable> outUsedVariables)
                = capture?.removeIf(info -> Handler.Marker.baseHandlers().stream().anyMatch(handler -> handler.isVariableOut(info.getVariable())) ||
                                            Syntax.Marker.syntaxHandlers().values().stream().anyMatch(handler -> handler.isVariableOut(info.getVariable())));
        
    }
    
    // Lookup support for generated nested classes
    public static class ElementFinder extends PsiElementFinder {
        
        private final JavaPsiFacade facade;
        
        public ElementFinder(final Project project) = facade = JavaPsiFacade.getInstance(project);
        
        @Override
        public @Nullable PsiClass findClass(final String qualifiedName, final GlobalSearchScope scope) {
            final int lastDot = qualifiedName.lastIndexOf('.');
            if (lastDot == -1)
                return null;
            final String outerName = qualifiedName.substring(0, lastDot), innerName = qualifiedName.substring(lastDot + 1);
            return innerName.isEmpty() || outerName.isEmpty() ? null : facade.findClass(outerName, scope)?.findInnerClassByName(innerName, false) ?? null;
        }
        
        @Override
        public PsiClass[] findClasses(final String qualifiedName, final GlobalSearchScope scope) = PsiClass.EMPTY_ARRAY;
        
    }
    
    @TransformProvider
    public static class InspectionTool implements InspectionToolProvider {
        
        public static class Handler extends AbstractBaseJavaLocalInspectionTool {
            
            @Getter
            @RequiredArgsConstructor
            @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
            public static class Checker extends JavaElementVisitor {
                
                ProblemsHolder holder;
                
                boolean isOnTheFly;
                
                QuickFixFactory quickFix = QuickFixFactory.getInstance();
                
                @Override
                public void visitReferenceExpression(final PsiReferenceExpression expression) = visitElement(expression);
                
                @Override
                public void visitElement(final PsiElement element) {
                    super.visitElement(element);
                    Syntax.Marker.syntaxHandlers().values().forEach(handler -> handler.check(element, holder, quickFix, isOnTheFly));
                    EntryPoint.check(element, holder, quickFix, isOnTheFly);
                }
                
            }
            
            @Override
            public String getDisplayName() = "Maho annotations inspection";
            
            @Override
            public String getShortName() = "MahoAnnotationsInspection";
            
            @Override
            public HighlightDisplayLevel getDefaultLevel() = HighlightDisplayLevel.ERROR;
            
            @Override
            public String[] getGroupPath() = { "Maho" };
            
            @Override
            public String getGroupDisplayName() = InspectionsBundle.message("group.names.compiler.issues");
            
            @Override
            public boolean isEnabledByDefault() = true;
            
            @Override
            public Checker buildVisitor(final ProblemsHolder holder, final boolean isOnTheFly) = { holder, isOnTheFly };
            
        }
        
        // The following code should probably be removed in the future
        
        @Redirect(target = "com.intellij.codeInspection.dataFlow.DataFlowInstructionVisitor", selector = "beforeExpressionPush", slice = @Slice(@At(insn = @At.Insn(opcode = ATHROW))))
        private static void beforeExpressionPush(final Throwable throwable) { }
        
        @Hook
        private static Hook.Result assertPhysical(final ProblemDescriptorBase $this, final PsiElement element) = Hook.Result.NULL;
        
        @Hook(at = @At(method = @At.MethodInsn(name = ASMHelper._INIT_)), before = false)
        private static Hook.Result _init_(
                final ProblemDescriptorBase $this,
                @Hook.Reference PsiElement startElement,
                @Hook.Reference PsiElement endElement,
                final String descriptionTemplate,
                final LocalQuickFix fixes[],
                final ProblemHighlightType highlightType,
                final boolean isAfterEndOfLine,
                @Hook.Reference @Nullable TextRange rangeInElement,
                final boolean showTooltip,
                final boolean onTheFly) {
            final PsiElement sourceStartElement = startElement, sourceEndElement = endElement;
            startElement = physicalElement(startElement);
            endElement = physicalElement(endElement);
            if (startElement != sourceStartElement || endElement != sourceEndElement)
                rangeInElement = null;
            if (startElement != null && startElement == endElement && startElement.getLanguage() == JavaLanguage.INSTANCE)
                while (startElement.getParent() != null && !(startElement.getParent() instanceof PsiClass) && startElement.getTextLength() == 0)
                    startElement = endElement = startElement.getParent();
            return { };
        }
        
        private static PsiElement physicalElement(final PsiElement element) {
            if (element instanceof final LightElement lightElement)
                return lightElement.equivalents().stream()
                        .filter(PsiAnnotation.class::isInstance)
                        .findFirst()
                        .or(() -> lightElement.equivalents().stream().findFirst())
                        .orElse(element);
            else if (element.getContainingFile() instanceof DummyHolder) {
                PsiElement outer = element.getContainingFile().getContext();
                while (outer != null) {
                    if (!(outer.getContainingFile() instanceof DummyHolder))
                        return outer;
                    outer = outer.getContainingFile().getContext();
                }
            }
            return element;
        }
        
        @Override
        public Class<? extends LocalInspectionTool>[] getInspectionClasses() = new Class[]{ Handler.class };
        
        @Hook
        private static Hook.Result getTokenizer(final JavaSpellcheckingStrategy $this, final PsiElement element)
                = Hook.Result.falseToVoid(amadeus.maho.lang.idea.handler.base.Handler.Marker.baseHandlers().stream().anyMatch(handler -> handler.isSuppressedSpellCheckingFor(element)), SpellcheckingStrategy.EMPTY_TOKENIZER);
        
    }
    
    @TransformProvider
    public static class EntryPoint extends PsiAugmentProvider {
        
        public static final Key<Boolean> transformedKey = { "transformed" };
        
        @Hook(value = DumbService.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
        private static boolean isDumbAware(final boolean capture, final Object o) = capture && !(o instanceof HighlightingPass);
        
        public static ASTNode transformASTNodes(final ASTNode astNode, final boolean loadingTreeElement) {
            if (astNode instanceof JavaFileElement) {
                for (ASTNode node = astNode.getFirstChildNode(); node != null; node = node.getTreeNext())
                    if (node instanceof final ClassElement targetNode)
                        Syntax.Marker.syntaxHandlers().values().forEach(handler -> handler.transformASTNode(targetNode, loadingTreeElement));
            } else if (astNode.getFirstChildNode() != null && astNode.getElementType().getLanguage() == JavaLanguage.INSTANCE)
                Syntax.Marker.syntaxHandlers().values().forEach(handler -> handler.transformASTNode(astNode, loadingTreeElement));
            return astNode;
        }
        
        public static final ThreadLocal<AtomicInteger> collectGuard = ThreadLocal.withInitial(AtomicInteger::new), loadTreeGuard = ThreadLocal.withInitial(AtomicInteger::new);
        
        @Hook(exactMatch = false)
        private static void buildStubTree_$Enter(final LightStubBuilder $this) = collectGuard.get().getAndIncrement();
        
        @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)), exactMatch = false)
        private static void buildStubTree_$Exit(final LightStubBuilder $this) = collectGuard.get().getAndDecrement();
        
        @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
        private static void ensureParsed(final LazyParseableElement $this) {
            if ($this.getElementType().getLanguage() == JavaLanguage.INSTANCE && loadTreeGuard.get().get() == 0)
                transform($this);
        }
        
        @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
        private static void doActualPsiChange(final DiffLog $this, final PsiFile file) {
            if (file.getLanguage() == JavaLanguage.INSTANCE)
                ((Privilege) $this.myEntries).forEach(entry -> {
                    switch (entry) {
                        case DiffLog.InsertEntry insert               -> transformASTNodes((Privilege) insert.myNewNode, false);
                        case DiffLog.ReplaceEntry replace             -> transformASTNodes((Privilege) replace.myNewChild, false);
                        case DiffLog.ReplaceElementWithEvents replace -> transformASTNodes((Privilege) replace.myNewRoot, false);
                        default                                       -> { }
                    }
                });
        }
        
        private static final ThreadLocal<LinkedList<Object>> reentrant = ThreadLocal.withInitial(LinkedList::new);
        
        @SneakyThrows
        @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
        private static void calcTreeElement(final PsiFileImpl $this) = transform($this);
        
        @Hook
        private static void loadTreeElement_$Enter(final PsiFileImpl $this) = loadTreeGuard.get().getAndIncrement();
        
        @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
        private static void loadTreeElement_$Exit(final PsiFileImpl $this) = loadTreeGuard.get().getAndDecrement();
        
        @Hook
        private static void getStubbedSpine_$Enter(final FileElement $this) = loadTreeGuard.get().getAndIncrement();
        
        @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
        private static void getStubbedSpine_$Exit(final FileElement $this) = loadTreeGuard.get().getAndDecrement();
        
        private static void transform(final ElementBase element) {
            if (collectGuard.get().get() == 0 && element.getUserData(transformedKey) == null) {
                // if ((!(element instanceof PsiElement psiElement) || psiElement.isPhysical()) && collectGuard.get().get() == 0 && element.getUserData(transformedKey) == null) {
                final LinkedList<Object> objects = reentrant.get();
                // final AtomicInteger count = loadTreeGuard.get();
                if (!objects.contains(element)) {
                    objects << element;
                    try {
                        // count.getAndIncrement();
                        IDEAContext.computeReadActionIgnoreDumbMode(() -> transformASTNodes(switch (element) {
                            case PsiElement psiElement -> psiElement.getNode();
                            case ASTNode astNode       -> astNode;
                            default                    -> throw new IllegalStateException("Unexpected value: " + element);
                        }, true));
                        element.putUserData(transformedKey, Boolean.TRUE);
                    } finally {
                        objects--;
                        // count.getAndDecrement();
                    }
                }
            }
        }
        
        public static void check(final PsiElement element, final ProblemsHolder holder, final QuickFixFactory quickFix, final boolean isOnTheFly) {
            if (element instanceof final PsiModifierListOwner owner)
                process(owner, (handler, target, annotation, annotationTree) -> handler.check(element, annotation, annotationTree, holder, quickFix));
        }
        
        private static final ThreadLocal<LinkedList<PsiClass>> collectMemberContextLocal = ThreadLocal.withInitial(LinkedList::new);
        
        @Hook(value = PsiClassImplUtil.class, isStatic = true, forceReturn = true)
        private static Object getMap(final PsiClass psiClass, final GlobalSearchScope scope) { // Consistent lambda with different identities
            if (!accessSourceAST()) {
                if (collectMemberContextLocal.get().isEmpty())
                    return CachedValuesManager.getProjectPsiDependentCache(psiClass, c -> ConcurrentFactoryMap.createMap((GlobalSearchScope s) -> (Privilege) new PsiClassImplUtil.MemberCache(c, s))).get(scope);
                return (Privilege) new PsiClassImplUtil.MemberCache(psiClass, scope);
            }
            return CachedValuesManager.getProjectPsiDependentCache(psiClass, c -> ConcurrentFactoryMap.createMap((GlobalSearchScope s) -> (Privilege) new PsiClassImplUtil.MemberCache(c, s))).get(scope);
        }
        
        private static ExtensibleMembers members(final ClassInnerStuffCache cache) = members((Privilege) cache.myClass);
        
        private static ExtensibleMembers members(final PsiExtensibleClass extensible) {
            ProgressManager.checkCanceled();
            if (!accessSourceAST()) {
                final var context = collectMemberContextLocal.get();
                if (!context[extensible]) {
                    context << extensible;
                    try {
                        return CachedValuesManager.getProjectPsiDependentCache(extensible, it -> IDEAContext.computeReadActionIgnoreDumbMode(() -> new ExtensibleMembers(it)));
                    } finally { context--; }
                }
            }
            return CachedValuesManager.getProjectPsiDependentCache(extensible, it -> new ExtensibleMembers(it, true));
        }
        
        @Hook
        private static Hook.Result getFields(final ClassInnerStuffCache $this) = { members($this).list(ExtensibleMembers.FIELDS).toArray(PsiField.EMPTY_ARRAY) };
        
        @Hook
        private static Hook.Result getMethods(final ClassInnerStuffCache $this) = { members($this).list(ExtensibleMembers.METHODS).toArray(PsiMethod.EMPTY_ARRAY) };
        
        @Hook
        private static Hook.Result getConstructors(final ClassInnerStuffCache $this) = { members($this).list(ExtensibleMembers.METHODS).stream().filter(PsiMethod::isConstructor).toArray(PsiMethod.ARRAY_FACTORY::create) };
        
        @Hook
        private static Hook.Result getInnerClasses(final ClassInnerStuffCache $this) = { members($this).list(ExtensibleMembers.INNER_CLASSES).toArray(PsiClass.EMPTY_ARRAY) };
        
        @Hook
        private static Hook.Result findFieldByName(final ClassInnerStuffCache $this, final String name, final boolean checkBases)
                = checkBases ? Hook.Result.VOID : new Hook.Result(members($this).map(ExtensibleMembers.FIELDS)[name]?.stream()?.findFirst()?.orElse(null) ?? null);
        
        @Hook
        private static Hook.Result findMethodsByName(final ClassInnerStuffCache $this, final String name, final boolean checkBases)
                = checkBases ? Hook.Result.VOID : new Hook.Result(members($this).list(ExtensibleMembers.METHODS).stream().filter(method -> method.getName().equals(name)).toArray(PsiMethod.ARRAY_FACTORY::create));
        
        @Hook
        private static Hook.Result findInnerClassByName(final ClassInnerStuffCache $this, final String name, final boolean checkBases)
                = checkBases ? Hook.Result.VOID : new Hook.Result(members($this).map(ExtensibleMembers.INNER_CLASSES)[name]?.stream()?.findFirst()?.orElse(null) ?? null);
        
        @Hook(value = PsiClassImplUtil.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true, metadata = @TransformMetadata(order = -1))
        private static boolean processDeclarationsInClass(
                final boolean capture,
                final PsiClass psiClass,
                final PsiScopeProcessor processor,
                final ResolveState state,
                final @Nullable Set<PsiClass> visited,
                final @Nullable PsiElement last,
                final PsiElement place,
                final LanguageLevel languageLevel,
                final boolean isRaw,
                final GlobalSearchScope resolveScope) {
            if (!capture)
                return false;
            if (psiClass instanceof final PsiExtensibleClass extensible) {
                if (last instanceof PsiTypeParameterList || last instanceof PsiModifierList && psiClass.getModifierList() == last || visited != null && visited.contains(psiClass))
                    return true;
                if (!(processor instanceof final MethodResolverProcessor methodResolverProcessor) || !methodResolverProcessor.isConstructor()) {
                    final ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
                    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.METHOD)) {
                        final @Nullable NameHint nameHint = processor.getHint(NameHint.KEY);
                        final @Nullable String name = nameHint == null ? null : nameHint.getName(state);
                        final ExtensibleMembers members = members(extensible);
                        final Stream<LightBridgeMethod> stream = members.map(ExtensibleMembers.BRIDGE_METHODS).values().stream()
                                .filter(it -> !it.isEmpty())
                                .map(it -> it[0])
                                .filter(it -> members.map(ExtensibleMembers.METHODS)[ExtensibleMembers.MethodKey.of(it)] == null);
                        return (name == null ? stream : stream.filter(method -> name.equals(method.getName()))).allMatch(method -> processor.execute(method, state));
                    }
                }
            }
            return true;
        }
        
        @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
        private static JavaResolveResult[] resolveToMethod(final JavaResolveResult capture[], final PsiReferenceExpressionImpl $this, final PsiFile containingFile) {
            if (capture.length > 1) {
                final JavaResolveResult results[] = Stream.of(capture)
                        .filterNot(result -> result.getElement() instanceof LightBridgeMethod)
                        .toArray(JavaResolveResult[]::new);
                return results.length == 0 ? capture : results;
            }
            return capture;
        }
        
        @Hook(value = JavaSharedImplUtil.class, isStatic = true, forceReturn = true)
        private static PsiType getType(final PsiTypeElement typeElement, final PsiElement anchor, final @Nullable PsiAnnotation stopAt) {
            @RequiredArgsConstructor
            @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
            class Key {
                
                PsiElement anchor;
                
                @Nullable PsiAnnotation stopAt;
                
                @Override
                public int hashCode() = ObjectHelper.hashCode(anchor, stopAt);
                
                @Override
                public boolean equals(final Object obj) = obj instanceof Key key && anchor == key.anchor && stopAt == key.stopAt;
                
            }
            final ConcurrentHashMap<Key, PsiType> cache = CachedValuesManager.getProjectPsiDependentCache(typeElement, _ -> new ConcurrentHashMap<>());
            final Key key = { anchor, stopAt };
            final @Nullable PsiType type = cache[key];
            if (type != null)
                return type;
            final PsiType result = wrapTypeIfNecessary(typeElement, anchor, stopAt, true);
            cache[key] = result;
            return result;
        }
        
        public static PsiType wrapTypeIfNecessary(final PsiTypeElement typeElement, final PsiElement anchor, final @Nullable PsiAnnotation stopAt, final boolean wrap) {
            final PsiType p_type[] = { typeElement.getType() };
            ((Privilege) JavaSharedImplUtil.collectAnnotations(anchor, stopAt))?.forEach(annotations -> p_type[0] = p_type[0].createArrayType().annotate(TypeAnnotationProvider.Static.create(annotations)));
            return wrap && typeElement.getParent() instanceof final PsiModifierListOwner owner ? p_type.let(result -> process(owner,
                    (handler, target, annotation, annotationTree) -> handler.wrapperType(typeElement, annotation, annotationTree, result)))[0] : p_type[0];
        }
        
        @Hook(value = PsiTypesUtil.class, isStatic = true)
        private static Hook.Result getExpectedTypeByParent(final PsiElement element) {
            final @Nullable PsiType expectedTypeByParent = expectedTypeByParent(element);
            if (expectedTypeByParent != null)
                return { expectedTypeByParent };
            return Hook.Result.VOID;
        }
        
        private static @Nullable PsiType expectedTypeByParent(final PsiElement element) {
            if (PsiUtil.skipParenthesizedExprUp(element.getParent()) instanceof final PsiVariable variable && (Privilege) PsiUtil.checkSameExpression(element, variable.getInitializer())) {
                final @Nullable PsiTypeElement typeElement = variable.getTypeElement();
                if (typeElement != null) {
                    if (typeElement.isInferredType())
                        return null;
                    final @Nullable PsiIdentifier identifier = variable.getNameIdentifier();
                    if (identifier == null)
                        return null;
                    return wrapTypeIfNecessary(typeElement, identifier, null, false);
                }
            }
            return null;
        }
        
        @Hook(value = HighlightUtil.class, isStatic = true, at = @At(method = @At.MethodInsn(name = "getType"), ordinal = 0), before = false, capture = true)
        private static PsiType checkVariableInitializerType(final PsiType capture, final PsiVariable variable) = unwrapTypeOrNull(variable) ?? capture;
        
        public static @Nullable PsiType unwrapType(final PsiVariable variable) = unwrapTypeOrNull(variable) ?? variable.getType();
        
        public static @Nullable PsiType unwrapTypeOrNull(final PsiVariable variable) = CachedValuesManager.getProjectPsiDependentCache(variable, it -> {
            final @Nullable PsiTypeElement typeElement = variable.getTypeElement();
            final @Nullable PsiIdentifier identifier = variable.getNameIdentifier();
            return typeElement != null && identifier != null ? wrapTypeIfNecessary(typeElement, identifier, null, false) : null;
        });
        
        @Override
        protected @Nullable PsiType inferType(final PsiTypeElement typeElement) = CachedValuesManager.getProjectPsiDependentCache(typeElement,
                it -> new PsiType[]{ null }.let(result -> Syntax.Marker.syntaxHandlers().values().forEach(handler -> handler.inferType(it, result)))[0]);
        
        private static final ThreadLocal<LinkedList<PsiModifierList>> transformModifiersContextLocal = ThreadLocal.withInitial(LinkedList::new);
        
        @Override
        protected Set<String> transformModifiers(final PsiModifierList modifierList, final Set<String> modifiers) {
            if (modifierList.getParent() instanceof final PsiModifierListOwner owner) {
                final var context = transformModifiersContextLocal.get();
                if (!context.contains(modifierList)) {
                    context.addLast(modifierList);
                    try {
                        return AccessibleHandler.transformPackageLocalToProtected(modifierList.getParent(),
                                new HashSet<>(modifiers).let(result -> process(owner, (handler, tree, annotation, annotationTree) -> handler.transformModifiers(tree, annotation, annotationTree, result))));
                    } finally { context.removeLast(); }
                }
            }
            return modifiers;
        }
        
        @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
        public static PsiClass[] getInterfaces(final PsiClass capture[], final PsiClassImpl $this) = IDEAContext.computeReadActionIgnoreDumbMode(() -> new HashSet<>(List.of(capture)).let(
                result -> process($this, (handler, tree, annotation, annotationTree) -> handler.transformInterfaces((PsiClass) tree, annotation, annotationTree, result))).toArray(PsiClass[]::new));
        
        @Hook(value = PsiClassImplUtil.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
        public static PsiClassType[] getImplementsListTypes(final PsiClassType capture[], final PsiClass $this) = IDEAContext.computeReadActionIgnoreDumbMode(() -> new HashSet<>(List.of(capture)).let(
                result -> process($this, (handler, tree, annotation, annotationTree) -> handler.transformInterfaceTypes((PsiClass) tree, annotation, annotationTree, result))).toArray(PsiClassType[]::new));
        
        public static void process(final PsiModifierListOwner tree, final Class<? extends Annotation> annotationType, final Consumer4<BaseHandler<Annotation>, PsiElement, Annotation, PsiAnnotation> consumer)
                = process(tree, it -> it.handler().value() == annotationType, consumer);
        
        public static void process(final PsiModifierListOwner tree, final Predicate<BaseHandler<?>> predicate = _ -> true, final Consumer4<BaseHandler<Annotation>, PsiElement, Annotation, PsiAnnotation> consumer)
                = Handler.Marker.baseHandlers().stream()
                .filter(predicate)
                .map(baseHandler -> getAnnotationsByTypeWithOuter(tree, baseHandler))
                .nonnull()
                .sorted((a, b) -> (int) (a.getKey().handler().priority() - b.getKey().handler().priority()))
                .forEach(entry -> entry.getValue().forEach(annotation -> consumer.accept(entry.getKey(), tree, annotation.v1, annotation.v2)));
        
        public static boolean hasAnnotation(final PsiModifierListOwner tree, final Class<? extends Annotation> annotationType) = !getAnnotationsByTypeWithOuter(tree, annotationType).isEmpty();
        
        public static boolean hasAnnotation(final PsiModifierListOwner tree, final BaseHandler<?> baseHandler) = getAnnotationsByTypeWithOuter(tree, baseHandler) != null;
        
        public static <A extends Annotation> List<Tuple2<A, PsiAnnotation>> getAnnotationsByTypeWithOuter(final PsiModifierListOwner tree, final Class<A> annotationType) {
            for (final BaseHandler<Annotation> baseHandler : Handler.Marker.baseHandlers())
                if (baseHandler.handler().value() == annotationType)
                    return getAnnotationsByTypeWithOuter(tree, (BaseHandler<A>) baseHandler)?.getValue() ?? List.<Tuple2<A, PsiAnnotation>>of();
            return getAnnotationsByType(tree, annotationType);
        }
        
        public static @Nullable <A extends Annotation> Map.Entry<BaseHandler<A>, List<Tuple2<A, PsiAnnotation>>> getAnnotationsByTypeWithOuter(final PsiModifierListOwner tree, final BaseHandler<A> baseHandler) {
            final var cache = CachedValuesManager.<PsiModifierListOwner, Map<BaseHandler<A>, Map.Entry<BaseHandler<A>, List<Tuple2<A, PsiAnnotation>>>>>getProjectPsiDependentCache(tree, _ -> new ConcurrentHashMap<>());
            @Nullable Map.Entry<BaseHandler<A>, List<Tuple2<A, PsiAnnotation>>> result = cache[baseHandler];
            if (result != null)
                return result;
            final List<Tuple2<A, PsiAnnotation>> annotations = getAnnotationsByType(tree, (Class<A>) baseHandler.handler().value());
            if (annotations.isEmpty()) {
                final Handler.Range ranges[] = baseHandler.handler().ranges();
                if (ranges.length > 0) {
                    final @Nullable PsiClass outer = PsiTreeUtil.getContextOfType(tree, PsiClass.class);
                    if (outer != null) {
                        for (final Handler.Range range : ranges) {
                            if (checkRange(range, tree))
                                if (baseHandler.derivedFilter(tree)) {
                                    final List<Tuple2<A, PsiAnnotation>> outerAnnotations = getAnnotationsByType(outer, (Class<A>) baseHandler.handler().value());
                                    return outerAnnotations.isEmpty() ? null : Map.entry(baseHandler, outerAnnotations);
                                } else
                                    return null;
                        }
                    }
                }
                return null;
            }
            cache[baseHandler] = result = Map.entry(baseHandler, annotations);
            return result;
        }
        
        public static <A extends Annotation> @Nullable A lookupAnnotation(final PsiModifierListOwner owner, final Class<A> annotationType)
                = getAnnotationsByTypeWithOuter(owner, annotationType).stream().findFirst().map(Tuple2::v1).orElse(null);
        
        public static final ThreadLocal<AtomicInteger> accessSourceASTCounterLocal = ThreadLocal.withInitial(AtomicInteger::new);
        
        public static final ThreadLocal<InvokeContext> accessSourceASTContextLocal = ThreadLocal.withInitial(() -> new InvokeContext(accessSourceASTCounterLocal.get()));
        
        public static boolean accessSourceAST() = accessSourceASTCounterLocal.get().get() > 0;
        
        public static <A extends Annotation> List<Tuple2<A, PsiAnnotation>> getAnnotationsByType(final PsiModifierListOwner tree, final Class<A> annotationType) {
            if (tree.getAnnotations().length == 0 && (!(tree instanceof PsiExtensibleClass) || !annotationType.isAnnotationPresent(Inherited.class)))
                return List.of();
            final var cache = CachedValuesManager.<PsiModifierListOwner, Map<Class<A>, List<Tuple2<A, PsiAnnotation>>>>getProjectPsiDependentCache(tree, _ -> new ConcurrentHashMap<>());
            @Nullable List<Tuple2<A, PsiAnnotation>> result = cache[annotationType]; // avoid computeIfAbsent deadlock
            if (result == null)
                cache[annotationType] = result = accessSourceASTContextLocal.get() ^ () -> {
                    final List<Tuple2<A, PsiAnnotation>> annotations = getAnnotationsByType(tree.getProject(), annotationType, tree.getAnnotations());
                    if (tree instanceof final PsiExtensibleClass extensibleClass && annotationType.isAnnotationPresent(Inherited.class) && extensibleClass.getSuperClass() instanceof final PsiExtensibleClass parent)
                        annotations *= getAnnotationsByType(parent, annotationType);
                    return annotations;
                };
            return result;
        }
        
        public static <A extends Annotation> List<Tuple2<A, PsiAnnotation>> getAnnotationsByType(final Project project, final Class<A> annotationType, final PsiAnnotation... annotations) {
            final LinkedList<Tuple2<A, PsiAnnotation>> result = { };
            for (final PsiAnnotation annotation : annotations)
                if (checkAnnotationType(annotationType, annotation)) {
                    final @Nullable A instance = AnnotationInvocationHandler.make(annotationType, annotation);
                    if (instance == null)
                        continue;
                    final Tuple2<A, PsiAnnotation> tuple = { instance, annotation };
                    result << tuple;
                }
            return result;
        }
        
        private static <A extends Annotation> boolean checkAnnotationType(final Class<A> annotationType, final PsiAnnotation annotation) {
            if (annotation.isValid() && annotationType.getCanonicalName().equals(annotation.getQualifiedName()))
                return true;
            final @Nullable PsiJavaCodeReferenceElement reference = annotation.getNameReferenceElement();
            if (reference != null && reference.isValid()) {
                final JavaResolveResult result = reference.advancedResolve(true);
                final @Nullable PsiElement element = result.getElement();
                if (element instanceof final PsiClass psiClass)
                    return annotationType.getCanonicalName().equals(psiClass.getQualifiedName());
            }
            return false;
        }
        
        private static boolean checkRange(final Handler.Range range, final PsiElement tree) = switch (tree) {
            case PsiField ignored  -> range == Handler.Range.FIELD;
            case PsiMethod ignored -> range == Handler.Range.METHOD;
            case PsiClass ignored  -> range == Handler.Range.CLASS;
            case null, default     -> false;
        };
        
    }
    
}
