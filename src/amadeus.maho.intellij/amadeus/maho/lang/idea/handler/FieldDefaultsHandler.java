package amadeus.maho.lang.idea.handler;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.impl.source.PsiModifierListImpl;
import com.intellij.psi.tree.IElementType;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.light.LightModifierList;
import amadeus.maho.lang.inspection.Nullable;

import static amadeus.maho.lang.idea.handler.FieldDefaultsHandler.PRIORITY;

@Handler(value = FieldDefaults.class, ranges = Handler.Range.FIELD, priority = PRIORITY)
public class FieldDefaultsHandler extends BaseHandler<FieldDefaults> {
    
    public static final int PRIORITY = -1 << 8;
    
    @Getter(lazy = true) // Should be called at least in the state COMPONENTS_LOADED, the current state is: CONFIGURATION_STORE_INITIALIZED
    private static final Map<IElementType, String> keywordMap = (Privilege) PsiModifierListImpl.KEYWORD_TYPE_TO_NAME_MAP;
    
    public static final List<String> accessModifiers = Stream.of(AccessLevel.values()).map(Enum::name).map(name -> name.toLowerCase(Locale.ENGLISH)).collect(Collectors.toList());
    
    @Override
    public void transformModifiers(final PsiElement tree, final FieldDefaults annotation, final @Nullable PsiAnnotation annotationTree, final HashSet<String> result) {
        if (tree instanceof PsiField && !result.contains(PsiModifier.STATIC)) {
            if (annotation.makeFinal())
                result.add(PsiModifier.FINAL);
            if (annotation.level() != AccessLevel.PACKAGE && result.stream().noneMatch(accessModifiers::contains)) {
                result.remove(PsiModifier.PACKAGE_LOCAL);
                result.add(annotation.level().name().toLowerCase(Locale.ENGLISH));
            }
        }
    }
    
    public static void transformModifiers(final LightModifierList modifierList, final FieldDefaults annotation) {
        if (annotation.makeFinal())
            modifierList.addModifier(PsiModifier.FINAL);
        if (annotation.level() != AccessLevel.PACKAGE && modifierList.modifiers().stream().noneMatch(accessModifiers::contains)) {
            modifierList.removeModifier(PsiModifier.PACKAGE_LOCAL);
            modifierList.addModifier(annotation.level().name().toLowerCase(Locale.ENGLISH));
        }
    }
    
    @Override
    public void check(final PsiElement tree, final FieldDefaults annotation, final PsiAnnotation annotationTree, final ProblemsHolder holder, final QuickFixFactory quickFix) {
        if (tree instanceof PsiClass context)
            if (context.isInterface())
                holder.registerProblem(annotationTree, JavaErrorBundle.message("not.allowed.in.interface"), ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
            else {
                final String level = annotation.level().name().toLowerCase(Locale.ENGLISH);
                final boolean makeFinal = annotation.makeFinal();
                Stream.of(context.getFields())
                        .map(PsiModifierListOwner::getModifierList)
                        .cast(PsiModifierListImpl.class)
                        .filter(list -> !list.hasModifierProperty(PsiModifier.STATIC))
                        .forEach(list -> Stream.of(list.getNode().getChildren(null)).forEach(astNode -> {
                            final IElementType elementType = astNode.getElementType();
                            final @Nullable String name = keywordMap()[elementType];
                            if (name != null) {
                                if (level.equals(name) || makeFinal && elementType == JavaTokenType.FINAL_KEYWORD)
                                    holder.registerProblem(astNode.getPsi(), "Duplicate modifier: " + name, ProblemHighlightType.WARNING, quickFix.createDeleteFix(astNode.getPsi()));
                            }
                        }));
            }
    }
    
}
