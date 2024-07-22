package amadeus.maho.lang.idea.handler;

import java.util.stream.Stream;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightFixUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMethodUtil;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJvmMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.impl.source.tree.java.PsiTypeCastExpressionImpl;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

import static amadeus.maho.util.bytecode.Bytecodes.INSTANCEOF;

@TransformProvider
public class PrivilegeHandler {
    
    public static boolean inPrivilege(final PsiElement element) {
        {
            @Nullable PsiElement target = element;
            while (target != null && !(target instanceof PsiFile)) {
                if (target instanceof PsiImportStatement || target instanceof PsiModifierListOwner owner && owner.hasAnnotation(Privilege.class.getCanonicalName()))
                    return true;
                target = target.getParent();
            }
        }
        final @Nullable PsiElement context = element instanceof PsiIdentifier ? element.getParent() : element;
        final PsiElement parent = PsiUtil.skipParenthesizedExprUp(context?.getParent() ?? context);
        return PsiUtil.skipParenthesizedExprUp((parent instanceof PsiAssignmentExpression assignmentExpression && assignmentExpression.getLExpression() == context ?
                assignmentExpression : parent instanceof PsiNewExpression newExpression ? newExpression : context).getParent()) instanceof PsiTypeCastExpression castExpression && isPrivilegeTypeCast(castExpression);
    }
    
    public static boolean isPrivilegeTypeCast(final PsiTypeCastExpression castExpression) {
        final @Nullable PsiTypeElement typeElement = castExpression.getCastType();
        return typeElement != null && CachedValuesManager.getProjectPsiDependentCache(typeElement, it -> isPrivilegeType(typeElement.getType()));
    }
    
    public static boolean isPrivilegeType(final PsiType type) = type instanceof PsiClassType classType && classType.resolve()?.getQualifiedName()?.equals(Privilege.class.getCanonicalName()) ?? false;
    
    @Hook(value = RedundantCastUtil.class, isStatic = true)
    private static Hook.Result isInPolymorphicCall(final PsiTypeCastExpression castExpression) = Hook.Result.falseToVoid(isPrivilegeTypeCast(castExpression));
    
    @Hook
    private static Hook.Result getType(final PsiTypeCastExpressionImpl $this) {
        final @Nullable PsiExpression operand = $this.getOperand();
        if (operand != null && isPrivilegeTypeCast($this))
            return { operand?.getType() ?? null };
        return Hook.Result.VOID;
    }
    
    @Hook(value = PsiUtil.class, isStatic = true)
    private static Hook.Result isStatement(final PsiElement element) = Hook.Result.falseToVoid(element instanceof PsiTypeCastExpression castExpression && isPrivilegeTypeCast(castExpression));
    
    @Hook(value = LambdaUtil.class, isStatic = true)
    private static Hook.Result isExpressionStatementExpression(final PsiElement element) = isStatement(element);
    
    @Hook(value = HighlightMethodUtil.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static @Nullable HighlightInfo.Builder checkConstructorCallsBaseClassConstructor(final @Nullable HighlightInfo.Builder capture, final PsiMethod method, final PsiResolveHelper resolveHelper) {
        if (capture != null && method.isConstructor() && method.hasAnnotation(Privilege.class.getCanonicalName())) {
            final @Nullable PsiClass superClass = method.getContainingClass()?.getSuperClass() ?? null;
            if (superClass != null && Stream.of(superClass.getMethods())
                    .filter(PsiMethod::isConstructor)
                    .anyMatch(constructor -> !constructor.hasParameters()))
                return null;
        }
        return capture;
    }
    
    @Hook(value = HighlightFixUtil.class, isStatic = true)
    private static void registerAccessQuickFixAction(final @Nullable HighlightInfo.Builder info, final TextRange fixRange, final PsiJvmMember refElement,
            final PsiJavaCodeReferenceElement place, final PsiElement fileResolveScope, final TextRange parentFixRange) {
        if (info != null) {
            final @Nullable PsiExpression target = switch (place.getParent()) {
                case PsiMethodCallExpression callExpression when callExpression.getMethodExpression() == place        -> callExpression;
                case PsiNewExpression newExpression when newExpression.getClassOrAnonymousClassReference() == place   -> newExpression;
                case PsiAssignmentExpression assignmentExpression when assignmentExpression.getLExpression() == place -> assignmentExpression;
                default                                                                                               -> place instanceof PsiExpression expression ? expression : null;
            };
            if (target != null)
                info.registerFix(QuickFixFactory.getInstance().createAddTypeCastFix(JavaPsiFacade.getElementFactory(place.getProject())
                        .createTypeByFQClassName(Privilege.class.getCanonicalName(), place.getResolveScope()), target), null, "Privilege", null, null);
        }
    }
    
    @Hook(at = @At(type = @At.TypeInsn(opcode = INSTANCEOF, type = PsiPrimitiveType.class)), before = false, capture = true, branchReversal = true)
    private static boolean visitTypeCastExpression(final boolean capture, final ControlFlowAnalyzer $this, final PsiTypeCastExpression expression) = capture || isPrivilegeTypeCast(expression);
    
    @Hook
    private static Hook.Result visitTypeCastExpression(final EvaluatorBuilderImpl.Builder $this, final PsiTypeCastExpression expression) {
        if (isPrivilegeTypeCast(expression)) {
            expression.getOperand()?.accept($this);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook(value = RedundantCastUtil.class, isStatic = true)
    private static Hook.Result isTypeCastSemantic(final PsiTypeCastExpression expression) {
        PsiElement parent = expression.getParent();
        while (parent.getParent() instanceof PsiParenthesizedExpression parenthesizedExpression)
            parent = parenthesizedExpression;
        if (parent.getParent() instanceof PsiJavaCodeReferenceElement referenceElement && referenceElement.getQualifier() == parent && referenceElement.getParent() instanceof PsiMethodCallExpression callExpression &&
            callExpression.getMethodExpression() == referenceElement && callExpression.resolveMethod()?.hasModifierProperty(PsiModifier.PRIVATE) ?? false)
            return Hook.Result.TRUE;
        return Hook.Result.VOID;
    }
    
}
