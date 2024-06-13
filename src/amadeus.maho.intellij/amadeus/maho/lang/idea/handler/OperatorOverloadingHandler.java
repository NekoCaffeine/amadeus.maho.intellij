package amadeus.maho.lang.idea.handler;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.LocalRefUseInfo;
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.codeInspection.OverwrittenKeyInspection;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.MethodContract;
import com.intellij.codeInspection.dataFlow.java.JavaDfaValueFactory;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.java.inst.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.jvm.problems.IndexOutOfBoundsProblem;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.PopInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.PushInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfGenericObjectType;
import com.intellij.codeInspection.dataFlow.types.DfIntType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.findUsages.JavaFindUsagesHelper;
import com.intellij.find.findUsages.JavaMethodFindUsagesOptions;
import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLambdaParameterType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiPostfixExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiUnaryExpression;
import com.intellij.psi.controlFlow.ControlFlowAnalyzer;
import com.intellij.psi.impl.compiled.ClsMethodImpl;
import com.intellij.psi.impl.source.PsiParameterImpl;
import com.intellij.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.impl.source.tree.java.PsiArrayAccessExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiAssignmentExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiBinaryExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiJavaTokenImpl;
import com.intellij.psi.impl.source.tree.java.PsiLambdaExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiPolyadicExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiPostfixExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiPrefixExpressionImpl;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.idea.IDEAContext;
import amadeus.maho.lang.idea.handler.base.JavaExpressionIndex;
import amadeus.maho.lang.idea.light.LightElementReference;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Redirect;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.Slice;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.DebugHelper;

import com.siyeh.ig.controlflow.SimplifiableBooleanExpressionInspection;
import com.siyeh.ig.numeric.UnaryPlusInspection;
import com.siyeh.ig.numeric.UnnecessaryUnaryMinusInspection;
import com.siyeh.ig.psiutils.ExpressionUtils;

import static amadeus.maho.lang.idea.IDEAContext.OperatorData.*;
import static com.intellij.psi.JavaTokenType.*;

@TransformProvider
public class OperatorOverloadingHandler {
    
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class OverloadInfo {
        
        PsiMethod navigation;
        
        LightElementReference reference;
        
        PsiType returnType;
        
        PsiMethodCallExpression expression;
        
        PsiExpression args[];
        
        @Nullable PsiExpression lower;
        
    }
    
    // # TypeConversionUtil
    
    @Hook(value = TypeConversionUtil.class, isStatic = true)
    private static Hook.Result isLValue(final PsiExpression expression) = Hook.Result.falseToVoid(specialLValue(expression));
    
    public static boolean specialLValue(final PsiExpression expression) = CachedValuesManager.getProjectPsiDependentCache(expression, it -> switch (it) {
        case PsiMethodCallExpression callExpression when callExpression.getArgumentList().isEmpty() -> hasSetter(callExpression);
        case PsiArrayAccessExpression accessExpression when overloadInfo(accessExpression) != null  -> hasPutter(accessExpression);
        default                                                                                     -> false;
    });
    
    private static boolean hasSetter(final PsiMethodCallExpression expression) {
        if (expression.getArgumentList().isEmpty() && expression.getMethodExpression().resolve() instanceof PsiMethod method) {
            final @Nullable PsiClass containingClass = method.getContainingClass();
            return containingClass != null && Stream.of(containingClass.findMethodsByName(method.getName(), true))
                    .anyMatch(it -> it.getParameterList().getParametersCount() == 1 && it.getParameterList().getParameter(0)?.getType()?.equals(method.getReturnType()) ?? false &&
                                    JavaResolveUtil.isAccessible(it, containingClass, it.getModifierList(), it, null, null));
        }
        return false;
    }
    
    private static boolean hasPutter(final PsiArrayAccessExpression expression) {
        final PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression);
        if (parent instanceof PsiAssignmentExpression || parent instanceof PsiUnaryExpression)
            return overloadInfo((PsiExpression) parent) != null;
        return false;
    }
    
    @Redirect(targetClass = JavaResolveCache.class, selector = "getType", slice = @Slice(@At(method = @At.MethodInsn(name = "isPolyExpression"))))
    private static boolean prohibitCaching(final PsiExpression expression) = expression instanceof PsiJavaCodeReferenceElement || PsiPolyExpressionUtil.isPolyExpression(expression);
    
    @Hook(forceReturn = true)
    private static boolean isValueCompatible(final PsiLambdaExpressionImpl $this) = CachedValuesManager.getProjectPsiDependentCache($this, lambda -> !(lambda.getBody() instanceof PsiCodeBlock block) || isValueCompatible(block));
    
    private static boolean isValueCompatible(final PsiCodeBlock block) {
        boolean hasReturn = false;
        for (final PsiReturnStatement statement : PsiUtil.findReturnStatements(block)) {
            if (statement.getReturnValue() != null)
                return true;
            hasReturn = true;
        }
        return !hasReturn && PsiTreeUtil.getPrevSiblingOfType(block.getLastBodyElement(), PsiStatement.class) instanceof PsiThrowStatement;
    }
    
    @Hook(value = PsiUtil.class, isStatic = true)
    private static Hook.Result isStatement(final PsiElement element) = avoid(element, true);
    
    @Hook(value = LambdaUtil.class, isStatic = true)
    private static Hook.Result isExpressionStatementExpression(final PsiElement element) = avoid(element, true);
    
    // # HighlightUtil
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkVariableExpected(final PsiExpression expr) = avoid(expr);
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkNotAStatement(final PsiStatement statement) = statement instanceof PsiExpressionStatement expressionStatement ? avoid(expressionStatement.getExpression()) : Hook.Result.VOID;
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkValidArrayAccessExpression(final PsiArrayAccessExpression expr) {
        final Hook.Result result = avoid(expr);
        if (result != Hook.Result.VOID)
            return result;
        if (expr.getParent() instanceof PsiAssignmentExpression assignmentExpression && assignmentExpression.getLExpression() == expr)
            return avoid(assignmentExpression);
        return result;
    }
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkAssignmentCompatibleTypes(final PsiAssignmentExpression expr) = avoid(expr);
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkAssignmentOperatorApplicable(final PsiAssignmentExpression expr) = avoid(expr);
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkUnaryOperatorApplicable(final PsiJavaToken token, final PsiExpression expr) = expr.getParent() instanceof PsiExpression ? avoid(expr.getParent()) : Hook.Result.VOID;
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkPolyadicOperatorApplicable(final PsiPolyadicExpression expr) = avoid(expr);
    
    public static Hook.Result avoid(final PsiElement element, final @Nullable Object result = null) = Hook.Result.falseToVoid(element instanceof PsiExpression expression && overloadInfo(expression) != null, result);
    
    // # HighlightControlFlowUtil
    
    @Hook(value = HighlightControlFlowUtil.class, isStatic = true)
    private static Hook.Result checkCannotWriteToFinal(final PsiExpression expr, final PsiFile containingFile) = Hook.Result.falseToVoid(overloadInfo(expr) != null, null);
    
    // # LocalRefUseInfo
    
    // fix local.variable.is.not.used.for.reading
    @Hook(value = LocalRefUseInfo.class, isStatic = true)
    private static Hook.Result isJustIncremented(final ReadWriteAccessDetector.Access access, final PsiElement refElement) = avoid(refElement.getParent(), false);
    
    // # EvaluatorBuilderImpl
    
    @Hook
    private static Hook.Result visitPrefixExpression(final EvaluatorBuilderImpl.Builder $this, final PsiPrefixExpression expression) = evaluator($this, expression);
    
    @Hook
    private static Hook.Result visitPostfixExpression(final EvaluatorBuilderImpl.Builder $this, final PsiPostfixExpression expression) = evaluator($this, expression);
    
    @Hook
    private static Hook.Result visitPolyadicExpression(final EvaluatorBuilderImpl.Builder $this, final PsiPolyadicExpression expression) = evaluator($this, expression);
    
    @Hook
    private static Hook.Result visitAssignmentExpression(final EvaluatorBuilderImpl.Builder $this, final PsiAssignmentExpression expression) = evaluator($this, expression);
    
    @Hook
    private static Hook.Result visitArrayAccessExpression(final EvaluatorBuilderImpl.Builder $this, final PsiArrayAccessExpression expression) = evaluator($this, expression);
    
    public static Hook.Result evaluator(final PsiElementVisitor visitor, final PsiExpression expr) {
        final @Nullable PsiMethodCallExpression expression = overloadInfo(expr)?.expression ?? null;
        if (expression != null) {
            expression.accept(visitor);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    // # PsiImpl
    
    @Hook
    private static Hook.Result getType(final PsiPrefixExpressionImpl $this) = typeResult($this);
    
    @Hook
    private static Hook.Result getType(final PsiPostfixExpressionImpl $this) = typeResult($this);
    
    @Hook
    private static Hook.Result getType(final PsiBinaryExpressionImpl $this) = typeResult($this);
    
    @Hook
    private static Hook.Result getType(final PsiPolyadicExpressionImpl $this) = typeResult($this);
    
    @Hook
    private static Hook.Result getType(final PsiAssignmentExpressionImpl $this) = typeResult($this);
    
    @Hook
    private static Hook.Result getType(final PsiArrayAccessExpressionImpl $this) = typeResult($this);
    
    public static Hook.Result typeResult(final PsiExpression expr) = Hook.Result.nullToVoid(overloadInfo(expr)?.returnType ?? null);
    
    private static final Set<IElementType> cannotOverload = Set.of(ANDAND, OROR, EQEQ, NE);
    
    public static boolean cannotOverload(final IElementType tag) = cannotOverload.contains(tag);
    
    public static boolean mayOverloadLValue(final PsiExpression operand, final boolean allowSetter = false)
            = !(operand.getType() instanceof PsiPrimitiveType) ||
              operand instanceof PsiArrayAccessExpression && overloadInfo(operand) != null ||
              allowSetter && operand instanceof PsiMethodCallExpression callExpression && callExpression.getArgumentList().isEmpty();
    
    public static boolean isNormalLValue(final PsiExpression lExpr) = lExpr instanceof PsiReferenceExpression || lExpr instanceof PsiArrayAccessExpression access && access.getArrayExpression().getType() instanceof PsiArrayType;
    
    public static @Nullable OverloadInfo calculateUnaryType(final PsiUnaryExpression expr) {
        if (expr.getOperand() == null)
            return null;
        final IElementType tokenType = expr.getOperationTokenType();
        final @Nullable String name = operatorType2operatorName[tokenType];
        if (name == null)
            return null;
        @Nullable OverloadInfo info = calculateExprType(expr, expr.getOperand(), expr instanceof PsiPostfixExpression ? name.replace("PRE", "POST") : name);
        if (info == null && (tokenType == PLUSPLUS || tokenType == MINUSMINUS)) {
            final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expr.getOperand());
            if (mayOverloadLValue(operand, true)) {
                final PsiElementFactory factory = JavaPsiFacade.getElementFactory(expr.getProject());
                final PsiExpression opExpr = factory.createExpressionFromText(STR."(\{operand.getText()}) \{tokenType == PLUSPLUS ? "+ 1" : "- 1"}", expr);
                if (opExpr.getType() != null)
                    info = isNormalLValue(operand) ? overloadInfo(opExpr) : fromAssOp(factory.createExpressionFromText(STR."(\{operand.getText()}) = (\{opExpr.getText()})", expr), opExpr);
            }
        }
        return info;
    }
    
    public static @Nullable OverloadInfo calculateBinaryType(final PsiBinaryExpression expr) {
        if (cannotOverload(expr.getOperationTokenType()) ||
            expr.getROperand() == null ||
            expr.getOperationTokenType() == PLUS &&
            expr.getLOperand().getType() instanceof PsiClassType classType &&
            classType.resolve() instanceof PsiClass psiClass &&
            String.class.getCanonicalName().equals(psiClass.getQualifiedName()))
            return null;
        final @Nullable String name = operatorType2operatorName[expr.getOperationTokenType()];
        return name == null ? null : calculateExprType(expr, expr.getLOperand(), name, expr.getROperand());
    }
    
    public static @Nullable OverloadInfo calculatePolyadicType(final PsiPolyadicExpression expr) {
        if (cannotOverload(expr.getOperationTokenType()) || expr.getOperands().length < 2 || expr.getOperands()[1] == null)
            return null;
        final @Nullable String name = operatorType2operatorName[expr.getOperationTokenType()];
        return name == null ? null : calculateExprType(expr, expr.getOperands()[0], name, expr.getOperands()[1]);
    }
    
    public static @Nullable OverloadInfo calculateAssignmentType(final PsiAssignmentExpression expr) {
        final PsiExpression lExpr = PsiUtil.skipParenthesizedExprDown(expr.getLExpression());
        if (expr.getRExpression() == null || lExpr instanceof PsiArrayAccessExpression access &&
                                             access.getArrayExpression().getType() instanceof PsiArrayType arrayType && arrayType.getComponentType() instanceof PsiPrimitiveType)
            return null;
        final IElementType token = expr.getOperationTokenType();
        @Nullable OverloadInfo info;
        if ((info = token == EQ ? lExpr instanceof PsiArrayAccessExpression access ?
                calculateExprType(expr, access.getArrayExpression(), "PUT", access.getIndexExpression(), expr.getRExpression()) : null :
                calculateExprType(expr, expr.getLExpression(), operatorType2operatorName[token], expr.getRExpression())) == null) {
            final @Nullable IElementType opToken = TypeConversionUtil.convertEQtoOperation(token);
            if (opToken != null && cannotOverload(opToken))
                return null;
            final PsiElementFactory factory = JavaPsiFacade.getElementFactory(expr.getProject());
            if (lExpr instanceof PsiMethodCallExpression callExpression && callExpression.getType() != null && callExpression.getArgumentList().isEmpty()) {
                final PsiExpression opExpr = factory.createExpressionFromText(opToken != null ?
                        STR."\{callExpression.getMethodExpression().getText()}(\{expr.getLExpression().getText()} \{operatorType2operatorSymbol[opToken]} \{expr.getRExpression().getText()})" :
                        STR."\{callExpression.getMethodExpression().getText()}(\{expr.getLExpression().getText()})", expr);
                return fromSetter(opExpr, callExpression.getType(), () -> new PsiExpression[]{ expr.getLExpression(), expr.getRExpression() });
            }
            if (opToken != null && mayOverloadLValue(lExpr)) {
                final PsiExpression opExpr = factory.createExpressionFromText(STR."(\{expr.getLExpression().getText()}) \{operatorType2operatorSymbol[opToken]} (\{expr.getRExpression().getText()})", expr);
                if (opExpr.getType() != null)
                    info = isNormalLValue(lExpr) ? overloadInfo(opExpr) : fromAssOp(factory.createExpressionFromText(STR."(\{expr.getLExpression().getText()}) = (\{opExpr.getText()})", expr), opExpr);
            }
        }
        return info;
    }
    
    private static @Nullable OverloadInfo fromAssOp(final PsiExpression assOpExpr, final PsiExpression opExpr) {
        @Nullable OverloadInfo info = null;
        if (assOpExpr.getType() != null)
            if ((info = overloadInfo(assOpExpr)) != null)
                info.returnType = (info.lower = opExpr).getType();
            else
                info = overloadInfo(opExpr);
        return info;
    }
    
    private static @Nullable OverloadInfo fromSetter(final PsiExpression opExpr, final PsiType returnType, final Supplier<PsiExpression[]> args) {
        if (opExpr instanceof PsiMethodCallExpression setCall && setCall.getType() != null) {
            final @Nullable PsiMethod method = setCall.resolveMethod();
            if (method != null && method.getParameterList().getParametersCount() == setCall.getArgumentList().getExpressionCount()) {
                final OverloadInfo info = { };
                final PsiExpressionList argumentList = setCall.getArgumentList();
                final MethodCandidateInfo candidateInfo = { method, PsiSubstitutor.EMPTY, false, false, argumentList, null, argumentList.getExpressionTypes(), null };
                info.reference = { setCall, _ -> new JavaResolveResult[]{ candidateInfo } };
                info.expression = setCall;
                info.returnType = returnType;
                info.navigation = method;
                info.args = args.get();
                return info;
            }
        }
        return null;
    }
    
    public static @Nullable OverloadInfo calculateAccessType(final PsiArrayAccessExpression expr)
            = expr.getIndexExpression() == null ? null : expr.getArrayExpression().getType() instanceof PsiArrayType ? null : calculateExprType(expr, expr.getArrayExpression(), "GET", expr.getIndexExpression());
    
    public static @Nullable OverloadInfo resolveExprType(final PsiElement element) = switch (element) {
        case PsiUnaryExpression unary           -> calculateUnaryType(unary);
        case PsiBinaryExpression binary         -> calculateBinaryType(binary);
        case PsiPolyadicExpression polyadic     -> calculatePolyadicType(polyadic);
        case PsiAssignmentExpression assignment -> calculateAssignmentType(assignment);
        case PsiArrayAccessExpression access    -> calculateAccessType(access);
        default                                 -> null;
    };
    
    public static boolean canResolve(final @Nullable PsiExpression expressions[] = expressions(expression), final PsiExpression expression) = expressions != null && Stream.of(expressions)
            .filter(expr -> !(expr instanceof PsiArrayAccessExpression))
            .map(expr -> expr?.getType() ?? null)
            .noneMatch(type -> type == null || type instanceof PsiLambdaParameterType);
    
    public static @Nullable OverloadInfo overloadInfo(final @Nullable PsiExpression expr) = expr == null || !canResolve(expr) ? null :
            CachedValuesManager.getProjectPsiDependentCache(expr, OperatorOverloadingHandler::resolveExprType);
    
    private static @Nullable PsiMethod resolveMethod(final @Nullable PsiMethodCallExpression expression)
            = expression != null && expression.resolveMethodGenerics() instanceof MethodCandidateInfo info &&
              (info.isValidResult() || info.getElement() instanceof ExtensionHandler.ExtensionMethod extensionMethod &&
                                       isFakeCallExpression(extensionMethod, expression)) && info.getInferenceErrorMessage() == null ? info.getElement() : null;
    
    private static boolean isFakeCallExpression(final ExtensionHandler.ExtensionMethod extensionMethod, final PsiMethodCallExpression callExpression)
            = callExpression.getArgumentList().getExpressionCount() != extensionMethod.sourceMethod().getParameterList().getParametersCount();
    
    public static @Nullable OverloadInfo calculateExprType(final PsiExpression element, final @Nullable PsiExpression expression, final @Nullable String name, final PsiExpression... expressions)
            = IDEAContext.computeReadActionIgnoreDumbMode(() -> {
        if (name == null || expression == null)
            return null;
        final PsiType expressionType = expression.getType();
        if (ArrayUtil.find(expressions, null) != -1 || expressionType instanceof PsiPrimitiveType && (expressions.length < 1 || expressions[0].getType() instanceof PsiPrimitiveType))
            return null;
        PsiMethodCallExpression overloadCall = null;
        @Nullable PsiMethod method = null;
        if (expressionType instanceof PsiPrimitiveType) {
            for (final PsiClass ext : ExtensionHandler.extensionSet(element.getResolveScope())) {
                overloadCall = createCallExpression(element, ext.getQualifiedName(), name, Stream.concat(Stream.of(expression), Stream.of(expressions)).map(PsiExpression::getText).collect(Collectors.joining(", ")));
                method = resolveMethod(overloadCall);
                if (method != null)
                    break;
            }
        } else {
            overloadCall = createCallExpression(element, STR."(\{expression.getText()})", name, Stream.of(expressions).map(PsiExpression::getText).collect(Collectors.joining(", ")));
            method = resolveMethod(overloadCall);
        }
        if (overloadCall == null || method == null)
            return null;
        final OverloadInfo result = { };
        result.navigation = method.getNavigationElement() instanceof PsiMethod navigation ? navigation : method;
        final PsiExpressionList argumentList = overloadCall.getArgumentList();
        final MethodCandidateInfo candidateInfo = { method, PsiSubstitutor.EMPTY, false, false, argumentList, null, argumentList.getExpressionTypes(), null };
        result.reference = { element, incompleteCode -> new JavaResolveResult[]{ candidateInfo } };
        if (method instanceof ExtensionHandler.ExtensionMethod extensionMethod) {
            final PsiMethod navigation = extensionMethod.sourceMethod();
            final @Nullable PsiClass owner = PsiTreeUtil.getContextOfType(navigation, PsiClass.class);
            if (owner != null) {
                final @Nullable PsiMethodCallExpression qualifiedCall = createCallExpression(element, owner.getQualifiedName(), name,
                        Stream.concat(Stream.of(expression), Stream.of(expressions)).map(PsiExpression::getText).collect(Collectors.joining(", ")));
                if (qualifiedCall != null && qualifiedCall.getType() != null)
                    overloadCall = qualifiedCall;
            }
        }
        final @Nullable PsiType callType = overloadCall.getType();
        if (callType == null)
            return null;
        result.returnType = callType;
        result.expression = overloadCall;
        result.args = ArrayHelper.insert(expressions, expression);
        return result;
    });
    
    public static @Nullable PsiMethodCallExpression createCallExpression(final PsiElement element, final Object... expressions) {
        try {
            final PsiElement result = PsiElementFactory.getInstance(element.getProject()).createExpressionFromText("%s.%s(%s)".formatted(expressions), element);
            return result instanceof PsiMethodCallExpression expression ? expression : null;
        } catch (final Throwable throwable) {
            if (!(throwable instanceof ProcessCanceledException))
                DebugHelper.breakpoint(throwable);
            return null;
        }
    }
    
    @Hook(isStatic = true, value = LambdaUtil.class)
    private static Hook.Result isValidLambdaContext(final PsiElement element)
            = Hook.Result.falseToVoid(element instanceof PsiUnaryExpression || element instanceof PsiPolyadicExpression || element instanceof PsiAssignmentExpression || element instanceof PsiArrayAccessExpression);
    
    // # SpellCheckerManager
    
    @Hook
    private static Hook.Result hasProblem(final SpellCheckerManager $this, final String word) = Hook.Result.falseToVoidReverse(operatorName2operatorType.containsKey(word));
    
    // # MethodUsagesSearcher
    
    @Hook(value = JavaFindUsagesHelper.class, isStatic = true)
    private static void processElementUsages(final PsiElement element, final FindUsagesOptions options, final Processor<? super UsageInfo> processor) {
        if (options instanceof JavaMethodFindUsagesOptions methodOptions && element instanceof PsiMethod method) {
            final PsiMethod target = element instanceof ClsMethodImpl clsMethod ? clsMethod.getSourceMirrorMethod() ?? method : method;
            if (!target.isConstructor()) {
                final Project project = PsiUtilCore.getProjectInReadAction(element);
                final String name = target.getName();
                final Map<VirtualFile, int[]> mapping = IDEAContext.computeReadActionIgnoreDumbMode(() -> {
                    final @Nullable String symbol = switch (name) {
                        case "GET",
                             "PUT" -> name;
                        default    -> operatorName2expressionTypes[name] != null ? name : null;
                    };
                    if (symbol != null) {
                        final HashMap<VirtualFile, int[]> offsets = { };
                        FileBasedIndex.getInstance().processValues(JavaExpressionIndex.INDEX_ID, symbol, null, (file, value) -> {
                            ProgressManager.checkCanceled();
                            offsets[file] = value.array();
                            return true;
                        }, GlobalSearchScopeUtil.toGlobalSearchScope(options.searchScope, project));
                        return offsets;
                    }
                    return Map.of();
                });
                if (!mapping.isEmpty()) {
                    final @Nullable Set<Class<? extends PsiExpression>> expressionTypes = switch (name) {
                        case "GET" -> Set.of(PsiArrayAccessExpressionImpl.class);
                        case "PUT" -> Set.of(PsiAssignmentExpressionImpl.class);
                        default    -> operatorName2expressionTypes[name];
                    };
                    final PsiManager manager = PsiManager.getInstance(project);
                    manager.runInBatchFilesMode(() -> {
                        mapping.entrySet().stream().anyMatch(entry -> {
                            ProgressManager.checkCanceled();
                            return !IDEAContext.computeReadActionIgnoreDumbMode(() -> {
                                if (manager.findFile(entry.getKey()) instanceof PsiJavaFile file)
                                    for (final int offset : entry.getValue())
                                        for (final Class<? extends PsiExpression> expressionType : expressionTypes) {
                                            final @Nullable PsiJavaToken token = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiJavaToken.class, false);
                                            if (token != null) {
                                                if (token.getParent() instanceof PsiExpression expression) {
                                                    final @Nullable OverloadInfo info = overloadInfo(expression);
                                                    if (info != null && (info.navigation == target || MethodSignatureUtil.isSuperMethod(target, info.navigation)))
                                                        if (!(Privilege) JavaFindUsagesHelper.addResult(expression, options, processor))
                                                            return false;
                                                }
                                            } else
                                                DebugHelper.breakpoint();
                                        }
                                return true;
                            });
                        });
                        return null;
                    });
                }
            }
        }
    }
    
    public static @Nullable PsiExpression[] expressions(final PsiElement element) = switch (element) {
        case PsiUnaryExpression unary           -> new PsiExpression[]{ unary.getOperand() };
        case PsiBinaryExpression binary         -> new PsiExpression[]{ binary.getLOperand(), binary.getROperand() };
        case PsiPolyadicExpression polyadic     -> polyadic.getOperands();
        case PsiAssignmentExpression assignment -> new PsiExpression[]{ assignment.getLExpression(), assignment.getRExpression() };
        case PsiArrayAccessExpression access    -> new PsiExpression[]{ access.getArrayExpression(), access.getIndexExpression() };
        default                                 -> null;
    };
    
    public static int lambdaIdx(final PsiExpression expressions[], final PsiElement element) {
        for (int i = 0; i < expressions.length; i++)
            if (PsiTreeUtil.isAncestor(expressions[i], element, false))
                return i;
        return -1;
    }
    
    @Hook(isStatic = true, value = LambdaUtil.class)
    private static Hook.Result getFunctionalInterfaceType(final PsiElement element, final boolean tryToSubstitute) {
        if (element.getParent() instanceof PsiExpression expression) {
            final @Nullable OverloadInfo info = overloadInfo(expression);
            if (info != null) {
                final PsiExpression expressions[] = info.expression.getArgumentList().getExpressions();
                final @Nullable PsiExpression operatorExpressions[] = expressions(expression);
                if (operatorExpressions != null) {
                    final int idx = lambdaIdx(operatorExpressions, element);
                    if (idx > -1 && (operatorExpressions.length > expressions.length ? idx == 0 ? info.expression.getMethodExpression().getQualifierExpression() :
                            expressions.length > idx - 1 ? expressions[idx - 1] : null : expressions.length > idx ? expressions[idx] : null) instanceof PsiFunctionalExpression functionalExpression)
                        return { functionalExpression.getFunctionalInterfaceType() };
                }
            }
        }
        return Hook.Result.VOID;
    }
    
    // # LeafPsiElement
    
    @Hook
    private static Hook.Result getReference(final LeafPsiElement $this)
            = $this instanceof PsiJavaTokenImpl token ? Hook.Result.nullToVoid(overloadInfo(PsiTreeUtil.getContextOfType(token, PsiExpression.class))?.reference?.dup(token) ?? null) : Hook.Result.VOID;
    
    // # com.intellij.psi.controlFlow.ControlFlowAnalyzer
    
    public static Hook.Result getResult(final ControlFlowAnalyzer $this, final PsiExpression expression) {
        final @Nullable OverloadInfo info = overloadInfo(expression);
        if (info == null)
            return Hook.Result.VOID;
        (Privilege) $this.startElement(expression);
        for (final PsiExpression arg : info.args)
            arg.accept($this);
        (Privilege) $this.emitEmptyInstruction();
        (Privilege) $this.generateExceptionJumps(expression, ExceptionUtil.getUnhandledExceptions(info.expression, expression.getParent()));
        (Privilege) $this.finishElement(expression);
        return Hook.Result.NULL;
    }
    
    @Hook
    private static Hook.Result visitPrefixExpression(final ControlFlowAnalyzer $this, final PsiPrefixExpression expression) = getResult($this, expression);
    
    @Hook
    private static Hook.Result visitPostfixExpression(final ControlFlowAnalyzer $this, final PsiPostfixExpression expression) = getResult($this, expression);
    
    @Hook
    private static Hook.Result visitPolyadicExpression(final ControlFlowAnalyzer $this, final PsiPolyadicExpression expression) = getResult($this, expression);
    
    @Hook
    private static Hook.Result visitAssignmentExpression(final ControlFlowAnalyzer $this, final PsiAssignmentExpression expression) = getResult($this, expression);
    
    @Hook
    private static Hook.Result visitArrayAccessExpression(final ControlFlowAnalyzer $this, final PsiArrayAccessExpression expression) = getResult($this, expression);
    
    // # com.intellij.psi.controlFlow.ControlFlowAnalyzer
    
    public static Hook.Result getResult(final com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer $this, final PsiExpression expression) {
        final @Nullable OverloadInfo info = overloadInfo(expression);
        if (info == null)
            return Hook.Result.VOID;
        final PsiMethodCallExpression methodCallExpression = info.expression;
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final JavaResolveResult result = methodExpression.advancedResolve(false);
        final @Nullable PsiMethod method = ObjectUtils.tryCast(result.getElement(), PsiMethod.class);
        final List<? extends MethodContract> contracts = method == null ? Collections.emptyList() : DfaUtil.addRangeContracts(method, JavaMethodContractUtil.getMethodCallContracts(method, methodCallExpression));
        final PsiParameter parameters[] = method != null ? method.getParameterList().getParameters() : null;
        final boolean isStatic = method != null && method.hasModifierProperty(PsiModifier.STATIC);
        (Privilege) $this.startElement(expression);
        (Privilege) $this.addConditionalErrorThrow();
        for (int i = 0; i < info.args.length; i++) {
            final PsiExpression arg = info.args[i];
            arg.accept($this);
            if (isStatic || i > 0)
                (Privilege) $this.generateBoxingUnboxingInstructionFor(arg, result.getSubstitutor().substitute(parameters[i - (isStatic ? 0 : 1)].getType()));
        }
        final JavaExpressionAnchor anchor = { expression };
        (Privilege) $this.addInstruction(new MethodCallInstruction(methodCallExpression, JavaDfaValueFactory.getExpressionDfaValue((Privilege) $this.myFactory, expression), contracts) {
            @Override
            public @Nullable DfaAnchor getDfaAnchor() = anchor;
        });
        (Privilege) $this.processFailResult(method, contracts, expression);
        (Privilege) $this.addMethodThrows(method);
        (Privilege) $this.addNullCheck(expression);
        if (info.lower != null) {
            if (!(methodCallExpression.getType() instanceof PsiPrimitiveType primitiveType && primitiveType.getKind() == JvmPrimitiveTypeKind.VOID))
                (Privilege) $this.addInstruction(new PopInstruction());
            (Privilege) $this.addInstruction(new PushInstruction(((Privilege) $this.myFactory).fromDfType(DfTypes.typedObject(info.lower.getType(), Nullability.NOT_NULL)), anchor));
        }
        (Privilege) $this.finishElement(expression);
        return Hook.Result.NULL;
    }
    
    @Hook
    private static Hook.Result visitPrefixExpression(final com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer $this, final PsiPrefixExpression expression) = getResult($this, expression);
    
    @Hook
    private static Hook.Result visitPostfixExpression(final com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer $this, final PsiPostfixExpression expression) = getResult($this, expression);
    
    @Hook
    private static Hook.Result visitPolyadicExpression(final com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer $this, final PsiPolyadicExpression expression) = getResult($this, expression);
    
    @Hook
    private static Hook.Result visitAssignmentExpression(final com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer $this, final PsiAssignmentExpression expression) = getResult($this, expression);
    
    @Hook
    private static Hook.Result visitArrayAccessExpression(final com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer $this, final PsiArrayAccessExpression expression) = getResult($this, expression);
    
    @Hook
    private static Hook.Result applyBoundsCheck(final IndexOutOfBoundsProblem $this, final DfaMemoryState state, final DfaValue array, final DfaValue index) {
        if (array.getDfType() instanceof DfGenericObjectType objectType && !objectType.getConstraint().isArray())
            return Hook.Result.TRUE;
        if (!(index.getDfType() instanceof DfIntType))
            return Hook.Result.TRUE;
        return Hook.Result.VOID;
    }
    
    // # PsiPrecedenceUtil
    
    @Hook(value = PsiPrecedenceUtil.class, isStatic = true)
    public static Hook.Result getPrecedence(final PsiExpression expression)
            = Hook.Result.falseToVoid(expression instanceof PsiTypeCastExpression castExpression && castExpression.getOperand() instanceof PsiLambdaExpression lambdaExpression &&
                                      lambdaExpression.getBody() instanceof PsiExpression, PsiPrecedenceUtil.LAMBDA_PRECEDENCE);
    
    // # Checker
    
    @Hook
    private static Hook.Result visitPrefixExpression(final SimplifiableBooleanExpressionInspection.SimplifiableBooleanExpressionVisitor $this, final PsiPrefixExpression expression)
            = Hook.Result.falseToVoid(overloadInfo(expression) != null || overloadInfo(PsiUtil.skipParenthesizedExprDown(expression.getOperand())) != null, null);
    
    @Hook(value = ExpressionUtils.class, isStatic = true)
    private static Hook.Result isStringConcatenation(final PsiElement element) = Hook.Result.falseToVoid(element instanceof PsiPolyadicExpression expression && expression.getOperationTokenType() != PLUS, false);
    
    @Hook(value = PsiParameterImpl.class, isStatic = true, forceReturn = true)
    private static PsiType getLambdaParameterType(final PsiParameter parameter) {
        final PsiElement parent = parameter.getParent();
        if (parent instanceof PsiParameterList list) {
            final int parameterIndex = list.getParameterIndex(parameter);
            if (parameterIndex > -1) {
                final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(parameter, PsiLambdaExpression.class);
                if (lambdaExpression != null) {
                    final @Nullable PsiType functionalInterfaceType = MethodCandidateInfo.ourOverloadGuard.doPreventingRecursion(parameter, false, () -> LambdaUtil.getFunctionalInterfaceType(lambdaExpression, true)) ??
                                                                      (MethodCandidateInfo.ourOverloadGuard.currentStack().contains(parameter) ? LambdaUtil.getFunctionalInterfaceType(lambdaExpression, true) : null);
                    final @Nullable PsiType type = lambdaExpression.getGroundTargetType(functionalInterfaceType);
                    if (type instanceof PsiIntersectionType intersectionType)
                        for (final PsiType conjunct : intersectionType.getConjuncts()) {
                            final @Nullable PsiType lambdaParameterFromType = LambdaUtil.getLambdaParameterFromType(conjunct, parameterIndex);
                            if (lambdaParameterFromType != null)
                                return lambdaParameterFromType;
                        }
                    else {
                        final @Nullable PsiType lambdaParameterFromType = LambdaUtil.getLambdaParameterFromType(type, parameterIndex);
                        if (lambdaParameterFromType != null)
                            return lambdaParameterFromType;
                    }
                }
            }
        }
        return new PsiLambdaParameterType(parameter);
    }
    
    @Hook
    private static Hook.Result visitPrefixExpression(final UnaryPlusInspection.UnaryPlusVisitor $this, final PsiPrefixExpression expression)
            = Hook.Result.falseToVoid(overloadInfo(expression) != null, null);
    
    @Hook
    private static Hook.Result visitPrefixExpression(final UnnecessaryUnaryMinusInspection.UnnecessaryUnaryMinusVisitor $this, final PsiPrefixExpression expression)
            = Hook.Result.falseToVoid(overloadInfo(expression) != null, null);
    
    @Hook
    private static Hook.Result processArraySequence(final OverwrittenKeyInspection.OverwrittenKeyVisitor $this, final PsiAssignmentExpression assignment, final PsiExpressionStatement statement, final @InspectionMessage String message)
            = Hook.Result.falseToVoid(overloadInfo(assignment) != null, statement);
    
}
