package amadeus.maho.lang.idea.light;

import com.intellij.psi.PsiExpression;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LightDefaultParameter extends LightParameter {
    
    PsiExpression defaultValue;
    
}
