package amadeus.maho.lang.idea.light;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiManager;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class LightIdentifier extends com.intellij.psi.impl.light.LightIdentifier {
    
    String text;
    
    public LightIdentifier(final PsiManager manager, final String text) {
        super(manager, text);
        this.text = text;
    }
    
    @Override
    public String getText() = text;
    
    public void setText(final String value) = text = value;
    
    @Override
    public LightIdentifier copy() = { getManager(), getText() };
    
    @Override
    public TextRange getTextRange() = TextRange.EMPTY_RANGE;
    
}
