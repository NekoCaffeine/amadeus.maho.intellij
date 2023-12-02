package amadeus.maho.lang.idea.handler.base;

import java.util.Collection;
import java.util.stream.Stream;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;

import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.idea.light.LightElementReference;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class InplaceRenameHandler extends VariableInplaceRenameHandler {
    
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
            if (resolved instanceof PsiNamedElement namedElement) {
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
            if (checkReference(at) || at instanceof PsiMultiReference reference && Stream.of(reference.getReferences()).anyMatch(InplaceRenameHandler::checkReference))
                return Hook.Result.FALSE;
        }
        return Hook.Result.VOID;
    }
    
    public static boolean checkReference(final @Nullable PsiReference at) = at instanceof LightElementReference || at instanceof PsiJavaCodeReferenceElement reference && reference.getText().equals("self");
    
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
