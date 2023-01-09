package amadeus.maho.lang.idea.light;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.SyntheticElement;
import com.intellij.psi.impl.light.LightElement;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.inspection.Nullable;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LightNameValuePair extends LightElement implements SyntheticElement, PsiNameValuePair {
    
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class LightAnnotationMemberValue extends LightElement implements SyntheticElement, PsiAnnotationMemberValue {
        
        String text;
        
        public LightAnnotationMemberValue(final PsiManager manager, final String text) {
            super(manager, JavaLanguage.INSTANCE);
            this.text = text;
        }
        
        @Override
        public String getText() = text;
        
        @Override
        public String toString() = "LightAnnotationMemberValue";
        
    }
    
    @Nullable String name;
    
    LightAnnotationMemberValue value;
    
    public LightNameValuePair(final PsiManager manager, final @Nullable String name, final String value) {
        super(manager, JavaLanguage.INSTANCE);
        this.name = name;
        this.value = { manager, value };
    }
    
    @Override
    public @Nullable PsiIdentifier getNameIdentifier() = null;
    
    @Override
    public @Nullable String getName() = name;
    
    @Override
    public @Nullable String getLiteralValue() = null;
    
    @Override
    public @Nullable PsiAnnotationMemberValue getValue() = value;
    
    @Override
    public PsiAnnotationMemberValue setValue(final PsiAnnotationMemberValue value) { throw new UnsupportedOperationException(); }
    
    @Override
    public String toString() = "LightNameValuePair";
    
}
