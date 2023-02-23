package amadeus.maho.lang.idea.handler;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.PsiTreeUtil;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.ExtensibleMembers;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.handler.base.HandlerMarker;
import amadeus.maho.lang.idea.light.LightMethod;
import amadeus.maho.lang.idea.light.LightModifierList;
import amadeus.maho.lang.idea.light.LightParameter;
import amadeus.maho.lang.inspection.Nullable;

import static amadeus.maho.lang.idea.handler.SetterHandler.PRIORITY;
import static com.intellij.psi.PsiModifier.STATIC;

@Handler(value = Setter.class, ranges = Handler.Range.FIELD, priority = PRIORITY)
public class SetterHandler extends BaseHandler<Setter> {
    
    public static final int PRIORITY = GetterHandler.PRIORITY << 2;
    
    @Override
    public void check(final PsiElement tree, final Setter annotation, final PsiAnnotation annotationTree, final ProblemsHolder holder, final QuickFixFactory quickFix) {
        if (tree instanceof PsiField field && field.hasModifierProperty(PsiModifier.FINAL) && annotationTree.getOwner() instanceof PsiElement owner && owner.getParent() instanceof PsiField) {
            if (HandlerMarker.EntryPoint.unwrapType(field)?.equals(field.getType()) ?? false)
                holder.registerProblem(annotationTree, JavaErrorBundle.message("assignment.to.final.variable", field.getName()), ProblemHighlightType.WARNING, quickFix.createDeleteFix(annotationTree));
        }
        if (tree instanceof PsiMethod method) {
            if (method.getParameterList().getParametersCount() != 0)
                holder.registerProblem(annotationTree, "The methods marked by @Setter must have no parameters.", ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
            if (tree.getParent() instanceof PsiClass parent && !parent.isInterface())
                holder.registerProblem(annotationTree, "The method marked by @Setter must be in the interface scope.", ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
            if (method.hasModifierProperty(STATIC))
                holder.registerProblem(annotationTree, "The method marked by @Setter must be non-static.", ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree),
                        quickFix.createModifierListFix(method, STATIC, false, false));
        }
    }
    
    @Override
    public void processVariable(final PsiField tree, final Setter annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) {
        final @Nullable PsiType unwrapType = HandlerMarker.EntryPoint.unwrapType(tree);
        if (unwrapType != null)
            if (!tree.hasModifierProperty(PsiModifier.FINAL) || !unwrapType.equals(tree.getType())) {
                final LightMethod methodTree = { tree, tree.getName(), tree, annotationTree };
                methodTree.setMethodReturnType(PsiTypes.voidType());
                methodTree.addParameter(tree.getName() + "$value", unwrapType, false);
                if (members.shouldInject(methodTree)) {
                    methodTree.setNavigationElement(tree);
                    methodTree.setContainingClass(context);
                    if (annotation.value() != AccessLevel.PACKAGE)
                        methodTree.addModifiers(annotation.value().name().toLowerCase(Locale.ENGLISH));
                    followStatic(tree, methodTree.getModifierList());
                    followAnnotationWithoutNullable(tree.getModifierList(), methodTree.getModifierList());
                    followAnnotation(annotationTree, "on", methodTree.getModifierList());
                    final @Nullable LightModifierList modifierList = (LightModifierList) methodTree.getParameterList().getParameter(0)?.getModifierList() ?? null;
                    if (modifierList != null)
                        followNullable(tree.getModifierList(), modifierList);
                    methodTree.setMethodKind(handler().value().getCanonicalName());
                    members.inject(methodTree);
                }
            }
    }
    
    @Override
    public void processMethod(final PsiMethod tree, final Setter annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) {
        final @Nullable PsiType returnType = tree.getReturnType();
        if (returnType != null && context.isInterface() && !tree.hasModifierProperty(STATIC) && tree.getParameterList().getParametersCount() == 0) {
            final LightMethod methodTree = { tree, tree.getName(), tree, annotationTree };
            final LightParameter parameter = { methodTree, "value", returnType, false };
            methodTree.addParameter(parameter);
            methodTree.setMethodReturnType(PsiTypes.voidType());
            if (members.shouldInject(methodTree)) {
                methodTree.setNavigationElement(tree);
                methodTree.setContainingClass(context);
                methodTree.addModifiers(PsiModifier.PUBLIC);
                methodTree.addModifiers(PsiModifier.ABSTRACT);
                followAnnotationWithoutNullable(tree.getModifierList(), methodTree.getModifierList());
                followAnnotation(annotationTree, "on", methodTree.getModifierList());
                followNullable(tree.getModifierList(), parameter.getModifierList());
                methodTree.setMethodKind(handler().value().getCanonicalName());
                members.inject(methodTree);
            }
        }
    }
    
    @Override
    public void collectRelatedTarget(final PsiModifierListOwner tree, final Setter annotation, final PsiAnnotation annotationTree, final Set<PsiNameIdentifierOwner> targets) = switch (tree) {
        case PsiField field   -> {
            final @Nullable PsiClass containingClass = field.getContainingClass();
            if (containingClass != null) {
                final @Nullable PsiType unwrapType = HandlerMarker.EntryPoint.unwrapType(field);
                if (unwrapType != null)
                    if (!field.hasModifierProperty(PsiModifier.FINAL) && !unwrapType.equals(field.getType())) {
                        final LightMethod methodTree = { field, field.getName(), field };
                        methodTree.setMethodReturnType(PsiTypes.voidType());
                        methodTree.addParameter(field.getName() + "$value", unwrapType, false);
                        targets += containingClass.findMethodBySignature(methodTree, false);
                    }
            }
        }
        case PsiMethod method -> {
            final @Nullable PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
                final @Nullable PsiType returnType = method.getReturnType();
                if (returnType != null && method.getContainingClass().isInterface() && !method.hasModifierProperty(STATIC) && method.getParameterList().getParametersCount() == 0) {
                    final LightMethod methodTree = { method, method.getName(), method };
                    final LightParameter parameter = { methodTree, "value", returnType, false };
                    methodTree.addParameter(parameter);
                    methodTree.setMethodReturnType(PsiTypes.voidType());
                    targets += containingClass.findMethodBySignature(methodTree, false);
                }
            }
        }
        default               -> { }
    };
    
    @Override
    public boolean isImplicitWrite(final PsiElement tree, final HandlerMarker.ImplicitUsageChecker.RefData refData) {
        if (tree instanceof PsiField field && HandlerMarker.EntryPoint.hasAnnotation(field, this)) {
            final @Nullable PsiClass owner = PsiTreeUtil.getContextOfType(tree, PsiClass.class);
            if (owner != null)
                return Stream.of(owner.findMethodsByName(field.getName(), false))
                        .filter(method -> method.getParameterList().getParametersCount() == 1 && PsiTypes.voidType().equals(method.getReturnType()))
                        .filter(LightMethod.class::isInstance)
                        .anyMatch(method -> method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.PROTECTED) || refData.localRefMap().get(tree).stream().anyMatch(reference -> reference.resolve() == method));
        }
        return false;
    }
    
}
