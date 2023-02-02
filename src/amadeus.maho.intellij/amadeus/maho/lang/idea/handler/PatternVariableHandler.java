package amadeus.maho.lang.idea.handler;

import java.util.HashSet;
import java.util.Set;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.source.tree.java.PsiPatternVariableImpl;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class PatternVariableHandler {
    
    @Hook(value = PsiAugmentProvider.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static Set<String> transformModifierProperties(final Set<String> capture, final PsiModifierList modifierList, final Project project, final Set<String> modifiers) {
        if (modifierList.getParent() instanceof PsiPatternVariableImpl && !capture.contains(PsiModifier.FINAL)) {
            final HashSet<String> result = { capture };
            result += PsiModifier.FINAL;
            return result;
        }
        return capture;
    }
    
}
