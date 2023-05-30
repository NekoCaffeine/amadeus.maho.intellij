package amadeus.maho.lang.idea.light;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.idea.IDEAContext;
import amadeus.maho.util.runtime.ObjectHelper;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LightParameter extends com.intellij.psi.impl.light.LightParameter implements LightElement {
    
    LightModifierList modifierList = { this };
    
    public LightParameter(final PsiElement context, final String name, final String type, final boolean isVarArgs)
            = super(name, mapEllipsisType(JavaPsiFacade.getElementFactory(context.getProject()).createTypeFromText(type, context), isVarArgs), context, JavaLanguage.INSTANCE, isVarArgs);
    
    public LightParameter(final PsiElement context, final String name, final PsiType type, final boolean isVarArgs) = super(name, mapEllipsisType(type, isVarArgs), context, JavaLanguage.INSTANCE, isVarArgs);
    
    public LightParameter(final PsiField context, final boolean isVarArgs) = this(context, context.getName(), mapEllipsisType(context.getType(), isVarArgs), isVarArgs);
    
    @Override
    public LightModifierList getModifierList() = modifierList;
    
    @Override
    public LightParameter copy() = { getDeclarationScope(), getName(), getType(), isVarArgs() };
    
    @Override
    public PsiElement setName(final String name) throws IncorrectOperationException = this;
    
    @Override
    public TextRange getTextRange() = TextRange.EMPTY_RANGE;
    
    @Override
    public boolean isValid() = true;
    
    @Override
    public boolean equals(final Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        final LightParameter parameter = (LightParameter) o;
        try {
            return IDEAContext.computeReadActionIgnoreDumbMode(() -> ObjectHelper.equals(getName(), parameter.getName()) && ObjectHelper.equals(getType(), parameter.getType()));
        } catch (final PsiInvalidElementAccessException exception) { return false; }
        
    }
    
    @Override
    public int hashCode() = ObjectHelper.hashCode(getName(), getType());
    
    public static PsiType mapEllipsisType(final PsiType type, final boolean isVarArgs) = isVarArgs && type instanceof PsiArrayType arrayType ? new PsiEllipsisType(arrayType.getComponentType(), arrayType.getAnnotations()) : type;
    
}
