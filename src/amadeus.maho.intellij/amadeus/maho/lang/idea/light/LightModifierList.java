package amadeus.maho.lang.idea.light;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierList;
import com.intellij.util.ArrayUtilRt;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;

import static com.intellij.psi.PsiModifier.MODIFIERS;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LightModifierList extends com.intellij.psi.impl.light.LightModifierList implements LightElement {
    
    Set<String> modifiers = new HashSet<>();
    
    List<PsiAnnotation> annotations = new LinkedList<>();
    
    @Override
    public void addModifier(final String modifier) = modifiers() += modifier;
    
    public void removeModifier(final String modifier) = modifiers() -= modifier;
    
    public void copyModifiers(final @Nullable PsiModifierList target) {
        if (target != null)
            Stream.of(MODIFIERS).filter(target::hasModifierProperty).forEach(this::addModifier);
    }
    
    @Override
    public void clearModifiers() = modifiers().clear();
    
    @Override
    public boolean hasModifierProperty(final String name) = modifiers()[name];
    
    @Override
    public boolean hasExplicitModifier(final String name) = modifiers()[name];
    
    @Override
    public @Nullable PsiFile getContainingFile() = getParent()?.getContainingFile() ?? null;
    
    @Override
    public String[] getModifiers() = ArrayUtilRt.toStringArray(modifiers());
    
    public void copyAnnotations(final @Nullable PsiModifierList target) {
        if (target != null)
            Stream.of(target.getAnnotations()).forEach(this::addAnnotation);
    }
    
    @Override
    public PsiAnnotation[] getAnnotations() = annotations().toArray(PsiAnnotation.ARRAY_FACTORY::create);
    
    public void addAnnotation(final PsiAnnotation annotation) = annotations().add(annotation);
    
    @Override
    public PsiAnnotation addAnnotation(final String qualifiedName) = PsiElementFactory.getInstance(getProject()).createAnnotationFromText(STR."@\{qualifiedName}", this).let(annotations()::add);
    
    @Override
    public PsiAnnotation findAnnotation(final String qualifiedName) = annotations().stream().filter(annotation -> annotation.hasQualifiedName(qualifiedName)).findFirst().orElse(null);
    
    @Override
    public boolean hasAnnotation(final String qualifiedName) = annotations().stream().anyMatch(annotation -> annotation.hasQualifiedName(qualifiedName));
    
    @Override
    public PsiAnnotation[] getApplicableAnnotations() = annotations().toArray(PsiAnnotation.ARRAY_FACTORY::create);
    
    @Override
    public boolean equals(final Object target) {
        if (this == target)
            return true;
        if (target == null || getClass() != target.getClass())
            return false;
        return Arrays.equals(getModifiers(), ((LightModifierList) target).getModifiers()) && annotations().equals(((LightModifierList) target).annotations());
    }
    
    @Override
    public int hashCode() = Objects.hash(Arrays.hashCode(getModifiers()), annotations());
    
    @Override
    public boolean isValid() = true;
    
}
