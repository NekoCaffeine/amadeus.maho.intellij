package amadeus.maho.lang.idea.light;

import java.util.List;
import java.util.stream.Stream;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.util.IncorrectOperationException;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.idea.IDEAContext;
import amadeus.maho.lang.inspection.Nullable;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LightField extends LightFieldBuilder implements LightElement {
    
    final LightModifierList modifierList = { this };
    
    final List<PsiElement> equivalents;
    
    @Setter
    @Nullable String mark;
    
    public LightField(final PsiElement context, final String name, final String type, final PsiElement... equivalents) {
        super(name, type, context);
        this.equivalents = List.of(equivalents);
    }
    
    public LightField(final PsiElement context, final String name, final PsiType type, final PsiElement... equivalents) {
        super(name, type, context);
        this.equivalents = List.of(equivalents);
    }
    
    @Override
    public boolean isEquivalentTo(final PsiElement another) = LightElement.equivalentTo(this, another) || super.isEquivalentTo(another);
    
    @Override
    public TextRange getTextRange() = TextRange.EMPTY_RANGE;
    
    @Override
    public LightModifierList getModifierList() = modifierList;
    
    @Override
    public boolean isValid() = getContainingClass()?.isValid() ?? true;
    
    @Override
    public @Nullable PsiFile getContainingFile() {
        if (!isValid())
            return null;
        try {
            return getContainingClass()?.getContainingFile() ?? null;
        } catch (final PsiInvalidElementAccessException e) { return null; }
    }
    
    @Override
    public boolean hasModifierProperty(final String name) = getModifierList().hasModifierProperty(name);
    
    public self addModifier(final String modifier) = getModifierList().addModifier(modifier);
    
    public self addModifiers(final String... modifiers) = Stream.of(modifiers).forEach(getModifierList()::addModifier);
    
    @Override
    public PsiElement copy() = new LightField(getNavigationElement(), getName(), getType(), equivalents.toArray(PsiElement.ARRAY_FACTORY::create)).let(it -> IDEAContext.followModifier(this, it.getModifierList()));
    
    @Override
    public PsiElement setName(final String name) throws IncorrectOperationException = this;
    
}
