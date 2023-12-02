package amadeus.maho.lang.idea.handler.base;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.search.GlobalSearchScope;

import amadeus.maho.lang.inspection.Nullable;

// Lookup support for generated nested classes
public class ElementFinder extends PsiElementFinder {
    
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
