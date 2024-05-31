package amadeus.maho.lang.idea.light;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMethodUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightTypeParameterListBuilder;
import com.intellij.util.IncorrectOperationException;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.idea.handler.DefaultValueHandler;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LightMethod extends LightMethodBuilder implements LightElement {
    
    String name;
    
    final LightModifierList modifierList = { this };
    
    final LightParameterList parameterList = { this };
    
    final LightReferenceList throwsList = { getManager(), PsiReferenceList.Role.THROWS_LIST };
    
    final List<PsiElement> equivalents;
    
    @Nullable String mark;
    
    boolean fieldInitialized;
    
    @Nullable PsiCodeBlock body;
    
    @Nullable PsiMethod source;
    
    @Nullable PsiElement navigationElement;
    
    public LightMethod(final PsiElement context, final String name, final PsiElement... equivalents) = this(context.getManager(), name, equivalents);
    
    public LightMethod(final PsiManager manager, final String name, final PsiElement... equivalents) {
        super(manager, JavaLanguage.INSTANCE, name);
        this.name = name;
        this.equivalents = List.of(equivalents);
    }
    
    public LightMethod(final PsiClass containingClass, final PsiMethod method, final PsiSubstitutor substitutor, final PsiElement... equivalents) {
        this(containingClass.getManager(), method.getName(), equivalents);
        source = method;
        modifierList.copyModifiers(method.getModifierList());
        modifierList.copyAnnotations(method.getModifierList());
        setContainingClass(containingClass);
        setNavigationElement(method);
        setMethodReturnType(substitutor.substitute(method.getReturnType()));
        Stream.of(method.getTypeParameters()).forEach(this::addTypeParameter);
        Stream.of(method.getParameterList().getParameters()).forEach(parameter -> addParameter(parameter, substitutor.substitute(parameter.getType())));
        Stream.of(method.getThrowsList().getReferencedTypes()).forEach(classType -> getThrowsList().addReference((PsiClassType) substitutor.substitute(classType)));
    }
    
    @Override
    public String getName() = name;
    
    @Override
    public LightMethod setMethodReturnType(final PsiType returnType) = (LightMethod) super.setMethodReturnType(returnType);
    
    @Override
    public void setNavigationElement(final PsiElement navigationElement) = this.navigationElement = navigationElement;
    
    @Override
    public PsiElement getNavigationElement() = navigationElement?.getNavigationElement() ?? navigationElement;
    
    @Override
    public boolean isEquivalentTo(final PsiElement another) = LightElement.equivalentTo(this, another) || super.isEquivalentTo(another);
    
    @Override
    public TextRange getTextRange() = TextRange.EMPTY_RANGE;
    
    @Override
    public LightParameterList getParameterList() = parameterList;
    
    @Override
    public LightModifierList getModifierList() = modifierList;
    
    public self addException(final PsiClassType type) = throwsList.addReference(type);
    
    public self addException(final String fqName) = throwsList.addReference(fqName);
    
    @Override
    public LightReferenceList getThrowsList() = throwsList;
    
    @Override
    public boolean isValid() = getContainingClass()?.isValid()??true;
    
    @Override
    public @Nullable PsiFile getContainingFile() {
        if (!isValid())
            return null;
        try {
            return getContainingClass()?.getContainingFile()??null;
        } catch (final PsiInvalidElementAccessException e) { return null; }
    }
    
    @Override
    public self addModifier(final String modifier) = getModifierList().addModifier(modifier);
    
    @Override
    public self addModifiers(final String... modifiers) = Stream.of(modifiers).forEach(getModifierList()::addModifier);
    
    @Override
    public self addParameter(final PsiParameter parameter) = getParameterList().addParameter(parameter);
    
    public self addParameter(final PsiParameter parameter, final PsiType type, final boolean isVarArgs = parameter.isVarArgs(), final PsiExpression defaultValue = DefaultValueHandler.defaultValue(parameter)) {
        if (defaultValue == null)
            addParameter(new LightParameter(this, parameter.getName(), type, isVarArgs));
        else
            addParameter(new LightDefaultParameter(this, parameter.getName(), type, isVarArgs, defaultValue));
    }
    
    public self addParameter(final String name, final String type, final boolean isVarArgs = false) = addParameter(new LightParameter(this, name, type, isVarArgs));
    
    @Override
    public self addParameter(final String name, final PsiType type, final boolean isVarArgs = false) = addParameter(new LightParameter(this, name, type, isVarArgs));
    
    @Override
    public LightTypeParameterListBuilder getTypeParameterList() = (LightTypeParameterListBuilder) super.getTypeParameterList();
    
    @Override
    public self addTypeParameter(final PsiTypeParameter parameter) = getTypeParameterList().addParameter(parameter);
    
    @Override
    public PsiCodeBlock getBody() = body;
    
    public void setBody(final PsiCodeBlock body) = this.body = body;
    
    public void setBody(final String body) = setBody(PsiElementFactory.getInstance(getProject()).createCodeBlockFromText(body, this));
    
    @Override
    public void delete() throws IncorrectOperationException { }
    
    @Override
    public void checkDelete() throws IncorrectOperationException { }
    
    @Override
    public LightMethod copy() = new LightMethod(getManager(), getName(), equivalents.toArray(PsiElement.ARRAY_FACTORY::create)).let(it -> {
        it.setNavigationElement(getNavigationElement());
        it.setMethodReturnType(it.getReturnType());
        Stream.of(getParameterList().getParameters()).forEach(it::addParameter);
        Stream.of(getTypeParameterList().getTypeParameters()).forEach(it::addTypeParameter);
        it.getModifierList().copyModifiers(getModifierList());
        it.getModifierList().copyAnnotations(getModifierList());
    });
    
    @Override
    public PsiElement setName(final String name) throws IncorrectOperationException = this;
    
    @Override
    public boolean equals(final Object target) {
        if (this == target)
            return true;
        if (target == null || getClass() != target.getClass())
            return false;
        final LightMethod method = (LightMethod) target;
        if (!getName().equals(method.getName()))
            return false;
        if (isConstructor() != method.isConstructor())
            return false;
        if (!Objects.equals(getContainingClass(), method.getContainingClass()))
            return false;
        if (!getModifierList().equals(method.getModifierList()))
            return false;
        return getParameterList().equals(method.getParameterList());
    }
    
    @Override
    public int hashCode() = 1;
    
    @Hook(value = HighlightMethodUtil.class, isStatic = true)
    private static Hook.Result registerChangeMethodSignatureFromUsageIntention(final PsiExpression expressions[], final HighlightInfo.Builder builder, final TextRange fixRange,
            final JavaResolveResult candidate, final PsiElement context) = Hook.Result.falseToVoid(!(candidate.getElement() instanceof LightElement), null);
    
}
