package amadeus.maho.lang.idea.light;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightElement;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LightExpression extends LightElement implements PsiExpression {
    
    PsiElement parent;
    
    PsiType type;
    
    @Override
    public @Nullable PsiType getType() = type;
    
    @Override
    public String toString() = "LightExpression";
    
    @Override
    public PsiElement getParent() = parent;
    
    @Override
    public String getText() = "<generated code>";
    
    @Override
    public void accept(final PsiElementVisitor visitor) {
        switch (visitor) {
            case JavaElementVisitor javaVisitor -> javaVisitor.visitExpression(this);
            default                             -> visitor.visitElement(this);
        }
    }
    
}
