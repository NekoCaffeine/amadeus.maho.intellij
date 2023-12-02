package amadeus.maho.lang.idea.handler.base;

import java.util.Collection;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.RelatedUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;

import amadeus.maho.lang.idea.light.LightElement;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class RenameLightElementProcessor extends RenamePsiElementProcessor {
    
    public boolean canProcessElement(final PsiElement element) = element instanceof LightElement && !(element.getNavigationElement() instanceof PsiAnnotation);
    
    public @Nullable PsiElement substituteElementToRename(final PsiElement element, final @Nullable Editor editor) = element.getNavigationElement();
    
    @Hook(value = RenameProcessor.class, isStatic = true)
    public static Hook.Result classifyUsages(final Collection<? extends PsiElement> elements, final Collection<UsageInfo> usages) {
        final MultiMap<PsiElement, UsageInfo> result = { };
        for (final UsageInfo usage : usages) {
            if (usage.getReference() instanceof com.intellij.psi.impl.light.LightElement)
                continue; //filter out implicit references (e.g. from derived class to super class' default constructor)
            final MoveRenameUsageInfo usageInfo = (MoveRenameUsageInfo) usage;
            if (usage instanceof RelatedUsageInfo relatedUsageInfo) {
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
