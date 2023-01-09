package amadeus.maho.lang.idea.light;

import java.util.function.Function;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.inspection.Nullable;

@Getter
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class LightElementReference extends LightElement implements PsiReference, PsiJavaReference {
    
    PsiExpression expression;
    
    Function<Boolean, JavaResolveResult[]> resolver;
    
    PsiElement context;
    
    public LightElementReference(final PsiExpression expression, final Function<Boolean, JavaResolveResult[]> resolver, final @Nullable PsiElement context = null) {
        super(expression.getManager(), expression.getLanguage());
        this.expression = expression;
        this.resolver = resolver;
        this.context = context;
    }
    
    public LightElementReference dup(final PsiElement context) = { expression, resolver, context };
    
    @Override
    public PsiElement getElement() = context == null ? expression : context;
    
    @Override
    public TextRange getRangeInElement() = context == null ? TextRange.EMPTY_RANGE : new TextRange(0, context.getTextLength());
    
    @Override
    public @Nullable PsiElement resolve() = advancedResolve(false)?.getElement() ?? null;
    
    @Override
    public String getCanonicalText() = resolve() instanceof PsiMethod method ? method.getName() : "";
    
    @Override
    public PsiElement handleElementRename(final String s) throws IncorrectOperationException { throw new IncorrectOperationException(); }
    
    @Override
    public PsiElement bindToElement(final PsiElement element) throws IncorrectOperationException { throw new IncorrectOperationException(); }
    
    @Override
    public boolean isReferenceTo(final PsiElement element) = element.getManager().areElementsEquivalent(element, resolve());
    
    @Override
    public boolean isSoft() = false;
    
    @Override
    public String toString() = String.format("%s{%s -> %s}", getClass().getSimpleName(), expression, resolve());
    
    @Override
    public void processVariants(final PsiScopeProcessor processor) { }
    
    @Override
    public @Nullable JavaResolveResult advancedResolve(final boolean incompleteCode) {
        final JavaResolveResult results[] = multiResolve(incompleteCode);
        return results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
    }
    
    @Override
    public JavaResolveResult[] multiResolve(final boolean incompleteCode) = resolver.apply(incompleteCode);
    
}
