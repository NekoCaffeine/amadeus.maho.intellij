package amadeus.maho.lang.idea.handler.base;

import java.util.HashSet;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceWrapper;
import com.intellij.psi.impl.search.MethodUsagesSearcher;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenameJavaMethodProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.idea.IDEAContext;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

import static amadeus.maho.lang.idea.IDEAContext.requiresMaho;

@TransformProvider
public class ExtensionReferenceSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
    
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
    
    public ExtensionReferenceSearcher() = super(true);
    
    @Override
    public void processQuery(final ReferencesSearch.SearchParameters parameters, final Processor<? super PsiReference> consumer)
            = search(parameters.getElementToSearch(), parameters.getScopeDeterminedByUser(), parameters.getOptimizer(), consumer);
    
    private static void search(final PsiElement elementToSearch, final SearchScope scope, final SearchRequestCollector optimizer, final Processor<? super PsiReference> consumer) {
        if (elementToSearch instanceof PsiModifierListOwner owner && owner instanceof PsiNamedElement namedElement) {
            final @Nullable String name = namedElement.getName();
            if (name != null) {
                final HashSet<PsiNameIdentifierOwner> targets = { };
                if (requiresMaho(elementToSearch)) {
                    HandlerSupport.process(owner, (handler, target, annotation, annotationTree) -> handler.collectRelatedTarget(target, annotation, annotationTree, targets));
                    Syntax.Marker.syntaxHandlers().values().forEach(handler -> handler.collectRelatedTarget(owner, targets));
                }
                targets -= null;
                targets.forEach(target -> {
                    if (target.getNameIdentifier() != null)
                        consumer.process(new PsiMemberReference<>(target, target, name));
                    if (target instanceof PsiMethod method)
                        OverridingMethodsSearch.search(method).forEach((Consumer<? super PsiMethod>) impl -> {
                            if (impl.getNameIdentifier() != null)
                                consumer.process(new PsiMemberReference<>(impl, impl, name));
                        });
                });
                targets.forEach(target -> ReferencesSearch.searchOptimized(target, scope.intersectWith(PsiSearchHelper.getInstance(target.getProject()).getUseScope(target)), false, optimizer,
                        reference -> consumer.process(new PsiReferenceWrapper(reference) {
                            
                            @Override
                            public PsiElement handleElementRename(final String newElementName) throws IncorrectOperationException
                                    = super.handleElementRename(reference.resolve() instanceof PsiNamedElement named ? remapName(newElementName, named, name) : newElementName);
                            
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
            if (usage.getElement() instanceof PsiMethod usageMethod && !name.equals(usageMethod.getName())) {
                usageMethod.setName(remapName(newName, usageMethod, name));
                return false;
            }
            return true;
        }).toArray(UsageInfo[]::new);
        return { };
    }
    
}
