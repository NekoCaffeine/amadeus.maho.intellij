package amadeus.maho.lang.idea.handler;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.codeStyle.JavaCodeStyleManagerImpl;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.util.PsiTreeUtil;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.ExtensibleMembers;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.handler.base.HandlerMarker;
import amadeus.maho.lang.idea.light.LightField;
import amadeus.maho.lang.idea.light.LightMethod;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.transform.mark.base.TransformProvider;

import static amadeus.maho.lang.idea.handler.GetterHandler.PRIORITY;
import static com.intellij.psi.PsiModifier.*;

@TransformProvider
@Handler(value = Getter.class, ranges = Handler.Range.FIELD, priority = PRIORITY)
public class GetterHandler extends BaseHandler<Getter> {
    
    public static final int PRIORITY = 1 << 2;
    
    public static final String REFERENCE_GETTER = "Reference", ON_REFERENCE_GETTER = "on" + REFERENCE_GETTER;
    
    @Override
    public void check(final PsiElement tree, final Getter annotation, final PsiAnnotation annotationTree, final ProblemsHolder holder, final QuickFixFactory quickFix) {
        if (tree instanceof PsiField field && annotation.lazy() && tree.getNode() instanceof CompositeElement element && element.findChildByRoleAsPsiElement(ChildRole.INITIALIZER) == null)
            holder.registerProblem(field.getNameIdentifier(), JavaErrorBundle.message("variable.not.initialized", field.getName()), ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
        if (tree instanceof PsiMethod method) {
            if (annotation.lazy())
                holder.registerProblem(annotationTree, "The @Getter marked on the method does not support the lazy attribute.", ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
            if (method.getParameterList().getParametersCount() != 0)
                holder.registerProblem(annotationTree, "The methods marked by @Getter must have no parameters.", ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
            if (tree.getParent() instanceof PsiClass psiClass && !psiClass.isInterface())
                holder.registerProblem(annotationTree, "The method marked by @Getter must be in the interface scope.", ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
            if (method.hasModifierProperty(STATIC))
                holder.registerProblem(annotationTree, "The method marked by @Getter must be non-static.", ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree),
                        quickFix.createModifierListFix(method, STATIC, false, false));
        }
    }
    
    @Override
    public void transformModifiers(final PsiElement tree, final Getter annotation, final PsiAnnotation annotationTree, final HashSet<String> result) {
        if (annotation.lazy())
            result.remove(FINAL);
    }
    
    @Override
    public void processVariable(final PsiField tree, final Getter annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) {
        final @Nullable PsiType unwrapType = HandlerMarker.EntryPoint.unwrapType(tree);
        if (unwrapType != null) {
            final LightMethod methodTree = { tree, tree.getName(), tree, annotationTree };
            methodTree.setMethodReturnType(unwrapType);
            if (members.shouldInject(methodTree)) {
                methodTree.setNavigationElement(tree);
                methodTree.setContainingClass(context);
                if (annotation.value() != AccessLevel.PACKAGE)
                    methodTree.addModifiers(annotation.value().name().toLowerCase(Locale.ENGLISH));
                if (!annotation.nonStatic())
                    followStatic(tree, methodTree.getModifierList());
                followAnnotation(annotationTree, "on", methodTree.getModifierList());
                followAnnotation(tree.getModifierList(), methodTree.getModifierList());
                methodTree.setMethodKind(handler().value().getCanonicalName());
                members.inject(methodTree);
            }
            if (annotation.lazy()) {
                final LightField mark = { tree, String.format("$%s$mark", tree.getName()), PsiTypes.booleanType(), annotationTree };
                if (members.shouldInject(mark)) {
                    mark.setNavigationElement(tree);
                    mark.setContainingClass(context);
                    mark.addModifiers(PRIVATE);
                    members.inject(mark);
                }
            }
            if (ReferenceHandler.findReferences(tree) > 0) {
                final LightMethod refMethodTree = { tree, tree.getName() + REFERENCE_GETTER, tree, annotationTree };
                refMethodTree.setMethodReturnType(tree.getType());
                if (members.shouldInject(refMethodTree)) {
                    refMethodTree.setNavigationElement(tree);
                    refMethodTree.setContainingClass(context);
                    if (annotation.value() != AccessLevel.PACKAGE)
                        refMethodTree.addModifiers(annotation.value().name().toLowerCase(Locale.ENGLISH));
                    if (!annotation.nonStatic())
                        followStatic(tree, refMethodTree.getModifierList());
                    followAnnotationWithoutNullable(tree.getModifierList(), refMethodTree.getModifierList());
                    followAnnotation(annotationTree, ON_REFERENCE_GETTER, refMethodTree.getModifierList());
                    refMethodTree.setMethodKind(handler().value().getCanonicalName());
                    members.inject(refMethodTree);
                }
            }
        }
    }
    
    @Override
    public void collectRelatedTarget(final PsiModifierListOwner tree, final Getter annotation, final PsiAnnotation annotationTree, final Set<PsiNameIdentifierOwner> targets) {
        if (tree instanceof final PsiField field) {
            final @Nullable PsiClass containingClass = field.getContainingClass();
            if (containingClass != null) {
                final @Nullable PsiType unwrapType = HandlerMarker.EntryPoint.unwrapType(field);
                if (unwrapType != null)
                    targets += containingClass.findMethodBySignature(new LightMethod(tree, field.getName()).let(it -> it.setMethodReturnType(unwrapType)), false);
                if (ReferenceHandler.findReferences(tree) > 0)
                    targets += containingClass.findMethodBySignature(new LightMethod(tree, field.getName() + REFERENCE_GETTER).let(it -> it.setMethodReturnType(field.getType())), false);
            }
        }
    }
    
    @Override
    public boolean isImplicitRead(final PsiElement tree, final HandlerMarker.ImplicitUsageChecker.RefData refData) {
        if (tree instanceof PsiField field && HandlerMarker.EntryPoint.hasAnnotation(field, this)) {
            final @Nullable PsiClass owner = PsiTreeUtil.getContextOfType(tree, PsiClass.class);
            if (owner != null)
                return Stream.concat(Stream.of(owner.findMethodsByName(field.getName(), false)), Stream.of(owner.findMethodsByName(((PsiField) tree).getName() + REFERENCE_GETTER, false)))
                        .filter(method -> method.getParameterList().getParametersCount() == 0 && !PsiTypes.voidType().equals(method.getReturnType()))
                        .anyMatch(method -> method.hasModifierProperty(PUBLIC) || method.hasModifierProperty(PROTECTED) || refData.localRefMap().get(tree).stream().anyMatch(reference -> reference.resolve() == method));
        }
        return false;
    }
    
    @Override
    public boolean isImplicitWrite(final PsiElement tree, final HandlerMarker.ImplicitUsageChecker.RefData refData) = tree instanceof PsiField field && HandlerMarker.EntryPoint.lookupAnnotation(field, Getter.class)?.lazy() ?? false;
    
    @Hook(value = JavaCodeStyleManagerImpl.class, isStatic = true)
    private static Hook.Result suggestUniqueVariableName(final String baseName, final PsiElement place, final boolean lookForward, final boolean allowShadowing, final Predicate<? super PsiVariable> canBeReused)
            = Hook.Result.falseToVoid(place instanceof PsiExpressionStatement statement && statement.getExpression() instanceof PsiMethodCallExpression callExpression && callExpression.getArgumentList().isEmpty(), baseName);
    
    @Hook(at = @At(method = @At.MethodInsn(name = "areElementsEquivalent")), before = false, capture = true)
    private static boolean visitReferenceExpression(final boolean capture, final @InvisibleType("com.intellij.refactoring.util.FieldConflictsResolver$1") JavaRecursiveElementVisitor $this,
            final PsiReferenceExpression expression) = capture && !(expression.resolve() instanceof PsiMethod);
    
}
