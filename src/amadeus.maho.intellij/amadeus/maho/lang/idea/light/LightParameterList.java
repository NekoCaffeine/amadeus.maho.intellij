package amadeus.maho.lang.idea.light;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.inspection.Nullable;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LightParameterList extends com.intellij.psi.impl.light.LightElement implements LightElement, PsiParameterList {
    
    final List<PsiParameter> parameters = new ArrayList<>();
    
    @Nullable PsiParameter cache[];
    
    public LightParameterList(final PsiElement context) = super(context.getManager(), context.getLanguage());
    
    public void addParameter(final PsiParameter parameter) {
        parameters.add(parameter);
        cache = null;
    }
    
    @Override
    public PsiParameter[] getParameters() = cache == null ? cache = parameters.toArray(PsiParameter.ARRAY_FACTORY::create) : cache;
    
    @Override
    public int getParameterIndex(final PsiParameter parameter) = parameters.indexOf(parameter);
    
    @Override
    public int getParametersCount() = getParameters().length;
    
    @Override
    public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        final LightParameterList list = (LightParameterList) o;
        return Objects.equals(parameters, list.parameters);
    }
    
    @Override
    public int hashCode() = Objects.hash(parameters);
    
    @Override
    public String toString() = "LightParameterList";
    
    public void accept(final PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor javaElementVisitor)
            javaElementVisitor.visitParameterList(this);
    }
    
}
