package amadeus.maho.lang.idea.light;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightElement;

import amadeus.maho.lang.inspection.Nullable;

public class LightLiteralExpression extends LightElement implements PsiLiteralExpression {
    
    @Nullable PsiType type;
    
    @Nullable Object value;
    
    public LightLiteralExpression(final PsiManager manager, final @Nullable PsiType type, final @Nullable Object value = null) {
        super(manager, JavaLanguage.INSTANCE);
        this.type = type;
        this.value = value;
    }
    
    @Override
    public @Nullable PsiType getType() = type;
    
    @Override
    public @Nullable Object getValue() = value;
    
    @Override
    public String getText() = value?.toString() ?? "null";
    
    @Override
    public String toString() = "LightLiteralExpression: " + getValue();
    
}
