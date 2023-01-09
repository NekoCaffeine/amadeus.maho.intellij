package amadeus.maho.lang.idea.light;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.Icon;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.light.LightPsiClassBuilder;
import com.intellij.util.IncorrectOperationException;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.inspection.Nullable;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LightClass extends LightPsiClassBuilder implements LightElement {
    
    final String qualifiedName;
    
    final LightModifierList modifierList = { this };
    
    final List<PsiElement> equivalents;
    
    final List<PsiField> fields = new ArrayList<>();
    
    @Nullable PsiField fieldsCache[];
    
    @Setter
    @Nullable String mark;
    
    public LightClass(final PsiElement context, final String name, final String qualifiedName, final PsiElement... equivalents) {
        super(context, name);
        this.qualifiedName = qualifiedName;
        this.equivalents = List.of(equivalents);
    }
    
    @Override
    public Icon getElementIcon(final int flags) = PsiClassImplUtil.getClassIcon(flags, this);
    
    @Override
    public LightModifierList getModifierList() = modifierList;
    
    public void addModifiers(final String... modifiers) = Stream.of(modifiers).forEach(getModifierList()::addModifier);
    
    public void addField(final LightField field) = fields.add(field);
    
    @Override
    public @Nullable PsiElement getParent() = getContainingClass();
    
    @Override
    public PsiElement getScope() = getContainingClass() != null ? getContainingClass().getScope() : super.getScope();
    
    @Override
    public PsiField[] getFields() = fieldsCache == null ? fieldsCache = fields.toArray(PsiField.ARRAY_FACTORY::create) : fieldsCache;
    
    @Override
    public String getQualifiedName() = qualifiedName;
    
    @Override
    public TextRange getTextRange() = TextRange.EMPTY_RANGE;
    
    @Override
    public @Nullable PsiFile getContainingFile() = equivalents.stream().map(PsiElement::getContainingFile).findFirst().orElse(null);
    
    @Override
    public boolean isEquivalentTo(final PsiElement another) = LightElement.equivalentTo(this, another) || super.isEquivalentTo(another);
    
    @Override
    public PsiElement setName(final String name) throws IncorrectOperationException = this;
    
    @Override
    public boolean equals(final Object target) {
        if (this == target)
            return true;
        if (target == null || getClass() != target.getClass())
            return false;
        return qualifiedName.equals(((LightClass) target).qualifiedName);
    }
    
    @Override
    public int hashCode() = qualifiedName.hashCode();
    
    @Override
    public boolean isValid() = true;
    
}
