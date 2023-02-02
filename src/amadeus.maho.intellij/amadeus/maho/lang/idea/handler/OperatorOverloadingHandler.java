package amadeus.maho.lang.idea.handler;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.RefCountHolder;
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiPostfixExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiUnaryExpression;
import com.intellij.psi.controlFlow.ControlFlowAnalyzer;
import com.intellij.psi.impl.java.stubs.index.JavaShortClassNameIndex;
import com.intellij.psi.impl.search.MethodUsagesSearcher;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.impl.source.tree.java.PsiArrayAccessExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiAssignmentExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiBinaryExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiJavaTokenImpl;
import com.intellij.psi.impl.source.tree.java.PsiPolyadicExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiPostfixExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiPrefixExpressionImpl;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.idea.IDEAContext;
import amadeus.maho.lang.idea.light.LightElementReference;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

import com.siyeh.ig.controlflow.SimplifiableBooleanExpressionInspection;
import com.siyeh.ig.psiutils.ExpressionUtils;

import static amadeus.maho.lang.idea.IDEAContext.OperatorData.*;
import static com.intellij.psi.JavaTokenType.*;

@TransformProvider
public class OperatorOverloadingHandler {
    
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class OverloadInfo {
        
        PsiElement navigation;
        
        LightElementReference reference;
        
        PsiType returnType;
        
        PsiMethodCallExpression expression;
        
        PsiExpression args[];
        
        @Nullable OverloadInfo lower;
        
    }
    
    // # TypeConversionUtil
    
    @Hook(value = TypeConversionUtil.class, isStatic = true)
    private static Hook.Result isLValue(final PsiExpression expression) = Hook.Result.falseToVoid(expression instanceof final PsiMethodCallExpression callExpression && hasSetter(callExpression));
    
    private static boolean hasSetter(final PsiMethodCallExpression callExpression) {
        if (callExpression.getArgumentList().isEmpty() && callExpression.getMethodExpression().resolve() instanceof final PsiMethod method) {
            final @Nullable PsiClass containingClass = method.getContainingClass();
            return containingClass != null && Stream.of(containingClass.findMethodsByName(method.getName(), true))
                    .anyMatch(it -> it.getParameterList().getParametersCount() == 1 && it.getParameterList().getParameter(0)?.getType()?.equals(method.getReturnType()) ?? false &&
                                    JavaResolveUtil.isAccessible(it, containingClass, it.getModifierList(), it, null, null));
        }
        return false;
    }
    
    @Hook(value = PsiUtil.class, isStatic = true)
    private static Hook.Result isStatement(final PsiElement element) = avoid(element, true);
    
    @Hook(value = LambdaUtil.class, isStatic = true)
    private static Hook.Result isExpressionStatementExpression(final PsiElement element) = avoid(element, true);
    
    // # HighlightUtil
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkVariableExpected(final PsiExpression expr) = avoid(expr);
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkNotAStatement(final PsiStatement statement) = statement instanceof final PsiExpressionStatement expressionStatement ? avoid(expressionStatement.getExpression()) : Hook.Result.VOID;
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkValidArrayAccessExpression(final PsiArrayAccessExpression expr) {
        final Hook.Result result = avoid(expr);
        if (result != Hook.Result.VOID)
            return result;
        if (expr.getParent() instanceof final PsiAssignmentExpression assignmentExpression && assignmentExpression.getLExpression() == expr)
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
    
    public static Hook.Result avoid(final PsiElement element, final @Nullable Object result = null) = Hook.Result.falseToVoid(element instanceof final PsiExpression expression && expr(expression) != null, result);
    
    // # HighlightControlFlowUtil
    
    @Hook(value = HighlightControlFlowUtil.class, isStatic = true)
    private static Hook.Result checkCannotWriteToFinal(final PsiExpression expr, final PsiFile containingFile) = Hook.Result.falseToVoid(expr(expr) != null, null);
    
    // # RefCountHolder
    
    // fix local.variable.is.not.used.for.reading
    @Hook(value = RefCountHolder.class, isStatic = true)
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
        final @Nullable PsiMethodCallExpression expression = expr(expr)?.expression ?? null;
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
    
    public static Hook.Result typeResult(final PsiExpression expr) = Hook.Result.nullToVoid(expr(expr)?.returnType ?? null);
    
    private static final Set<IElementType> cannotOverload = Set.of(ANDAND, OROR);
    
    public static boolean cannotOverload(final IElementType tag) = cannotOverload.contains(tag);
    
    public static @Nullable OverloadInfo calculateUnaryType(final PsiUnaryExpression expr) {
        if (expr.getOperand() == null)
            return null;
        final String name = operatorType2operatorName[expr.getOperationTokenType()];
        assert name != null;
        return calculateExprType(expr, expr.getOperand(), expr instanceof PsiPostfixExpression ? name.replace("PRE", "POST") : name);
    }
    
    public static @Nullable OverloadInfo calculateBinaryType(final PsiBinaryExpression expr) {
        if (cannotOverload(expr.getOperationTokenType()) || expr.getROperand() == null)
            return null;
        final String name = operatorType2operatorName[expr.getOperationTokenType()];
        assert name != null;
        return calculateExprType(expr, expr.getLOperand(), name, expr.getROperand());
    }
    
    public static @Nullable OverloadInfo calculatePolyadicType(final PsiPolyadicExpression expr) {
        if (cannotOverload(expr.getOperationTokenType()) || expr.getOperands().length < 2 || expr.getOperands()[1] == null)
            return null;
        final String name = operatorType2operatorName[expr.getOperationTokenType()];
        assert name != null;
        return calculateExprType(expr, expr.getOperands()[0], name, expr.getOperands()[1]);
    }
    
    public static @Nullable OverloadInfo calculateAssignmentType(final PsiAssignmentExpression expr) {
        if (expr.getRExpression() == null)
            return null;
        final IElementType token = expr.getOperationTokenType();
        @Nullable OverloadInfo info;
        if ((info = token == EQ ? PsiUtil.skipParenthesizedExprDown(expr.getLExpression()) instanceof final PsiArrayAccessExpression access ?
                calculateExprType(expr, access.getArrayExpression(), "PUT", access.getIndexExpression(), expr.getRExpression()) : null :
                calculateExprType(expr, expr.getLExpression(), operatorType2operatorName[token], expr.getRExpression())) == null) {
            final @Nullable IElementType opToken = TypeConversionUtil.convertEQtoOperation(token);
            if (opToken == null || cannotOverload(opToken))
                return null;
            final PsiElementFactory factory = JavaPsiFacade.getElementFactory(expr.getProject());
            final PsiExpression opExpr = factory.createExpressionFromText(String.format("(%s) %s (%s)", expr.getLExpression().getText(), operatorType2operatorSymbol[opToken], expr.getRExpression().getText()), expr);
            if (opExpr.getType() != null) {
                final PsiExpression assOpExpr = factory.createExpressionFromText(String.format("(%s) = (%s)", expr.getLExpression().getText(), opExpr.getText()), expr);
                if (assOpExpr.getType() != null) {
                    info = expr(assOpExpr);
                    if (info != null)
                        info.lower = expr(opExpr);
                    else
                        info = expr(opExpr);
                }
            }
        }
        if (info == null && PsiUtil.skipParenthesizedExprDown(expr.getLExpression()) instanceof final PsiMethodCallExpression callExpression && callExpression.getType() != null && callExpression.getArgumentList().isEmpty()) {
            final @Nullable IElementType opToken = TypeConversionUtil.convertEQtoOperation(token);
            final @Nullable PsiExpression opExpr = JavaPsiFacade.getElementFactory(expr.getProject())
                    .createExpressionFromText(String.format("%s(%s %s %s)", callExpression.getMethodExpression().getText(), expr.getLExpression().getText(), operatorType2operatorSymbol[opToken], expr.getRExpression().getText()), expr);
            if (opExpr instanceof final PsiMethodCallExpression setCall && setCall.getType() != null) {
                final @Nullable PsiMethod method = setCall.resolveMethod();
                if (method != null) {
                    info = { };
                    final PsiExpressionList argumentList = setCall.getArgumentList();
                    final MethodCandidateInfo candidateInfo = { method, PsiSubstitutor.EMPTY, false, false, argumentList, null, argumentList.getExpressionTypes(), null };
                    info.reference = { setCall, incompleteCode -> new JavaResolveResult[]{ candidateInfo } };
                    info.expression = setCall;
                    info.returnType = callExpression.getType();
                    info.navigation = method;
                    info.args = { expr.getLExpression(), expr.getRExpression() };
                }
            }
        }
        return info;
    }
    
    public static @Nullable OverloadInfo calculateAccessType(final PsiArrayAccessExpression expr) {
        if (expr.getIndexExpression() == null)
            return null;
        return calculateExprType(expr, expr.getArrayExpression(), "GET", expr.getIndexExpression());
    }
    
    public static @Nullable OverloadInfo resolveExprType(final PsiElement element) = switch (element) {
        case final PsiUnaryExpression unary           -> calculateUnaryType(unary);
        case final PsiBinaryExpression binary         -> calculateBinaryType(binary);
        case final PsiPolyadicExpression polyadic     -> calculatePolyadicType(polyadic);
        case final PsiAssignmentExpression assignment -> calculateAssignmentType(assignment);
        case final PsiArrayAccessExpression access    -> calculateAccessType(access);
        default                                 -> null;
    };
    
    public static @Nullable OverloadInfo expr(final @Nullable PsiExpression expr) = expr == null ? null :
            // CachedValuesManager.getProjectPsiDependentCache(expr, OperatorOverloadingHandler::resolveExprType);
            MethodCandidateInfo.isOverloadCheck() ? resolveExprType(expr) : CachedValuesManager.getProjectPsiDependentCache(expr, OperatorOverloadingHandler::resolveExprType);
    
    private static @Nullable PsiMethod resolveMethod(final @Nullable PsiMethodCallExpression expression)
            = expression != null && expression.resolveMethodGenerics() instanceof final MethodCandidateInfo info &&
              (info.isValidResult() || info.getElement() instanceof final ExtensionHandler.ExtensionMethod extensionMethod && isFakeCallExpression(extensionMethod, expression))
              && info.getInferenceErrorMessage() == null ? info.getElement() : null;
    
    private static boolean isFakeCallExpression(final ExtensionHandler.ExtensionMethod extensionMethod, final PsiMethodCallExpression callExpression)
            = callExpression.getArgumentList().getExpressionCount() != extensionMethod.sourceMethod().getParameterList().getParametersCount();
    
    public static @Nullable OverloadInfo calculateExprType(final PsiExpression element, final @Nullable PsiExpression expression, final String name, final PsiExpression... expressions)
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
                overloadCall = expr(element, ext.getQualifiedName(), name, Stream.concat(Stream.of(expression), Stream.of(expressions))
                        .map(PsiExpression::getText)
                        .collect(Collectors.joining(", ")));
                method = resolveMethod(overloadCall);
                if (method != null)
                    break;
            }
        } else {
            overloadCall = expr(element, String.format("(%s)", expression.getText()), name, Stream.of(expressions)
                    .map(PsiExpression::getText)
                    .collect(Collectors.joining(", ")));
            method = resolveMethod(overloadCall);
        }
        if (overloadCall == null || method == null)
            return null;
        final OverloadInfo result = { };
        result.navigation = method.getNavigationElement() instanceof final PsiMethod navigation ? navigation : method;
        final PsiExpressionList argumentList = overloadCall.getArgumentList();
        final MethodCandidateInfo candidateInfo = { method, PsiSubstitutor.EMPTY, false, false, argumentList, null, argumentList.getExpressionTypes(), null };
        result.reference = { element, incompleteCode -> new JavaResolveResult[]{ candidateInfo } };
        if (method instanceof final ExtensionHandler.ExtensionMethod extensionMethod) {
            final PsiMethod navigation = extensionMethod.sourceMethod();
            final @Nullable PsiClass owner = PsiTreeUtil.getContextOfType(navigation, PsiClass.class);
            if (owner != null) {
                overloadCall = expr(element, owner.getQualifiedName(), name, Stream.concat(Stream.of(expression), Stream.of(expressions))
                        .map(PsiExpression::getText)
                        .collect(Collectors.joining(", ")));
                if (overloadCall == null)
                    return null;
            }
        }
        final @Nullable PsiType callType = overloadCall.getType();
        if (callType == null)
            return null;
        result.returnType = callType;
        result.expression = overloadCall;
        result.args = expressions;
        return result;
    });
    
    public static @Nullable PsiMethodCallExpression expr(final PsiElement element, final Object... expressions) {
        try {
            final PsiElement result = PsiElementFactory.getInstance(element.getProject()).createExpressionFromText(String.format("%s.%s(%s)", expressions), element);
            return result instanceof final PsiMethodCallExpression expression ? expression : null;
        } catch (final Throwable throwable) {
            if (!(throwable instanceof ProcessCanceledException))
                throwable.printStackTrace();
            return null;
        }
    }
    
    @Hook(isStatic = true, value = LambdaUtil.class)
    private static Hook.Result isValidLambdaContext(final PsiElement element) = Hook.Result.falseToVoid(
            element instanceof PsiUnaryExpression ||
            element instanceof PsiPolyadicExpression ||
            element instanceof PsiAssignmentExpression ||
            element instanceof PsiArrayAccessExpression);
    
    // # SpellCheckerManager
    
    @Hook
    private static Hook.Result hasProblem(final SpellCheckerManager $this, final String word) = Hook.Result.falseToVoidReverse(operatorName2operatorType.containsKey(word));
    
    // # MethodUsagesSearcher
    
    @Hook
    private static void processQuery(final MethodUsagesSearcher $this, final MethodReferencesSearch.SearchParameters parameters, final Processor<PsiReference> consumer) = IDEAContext.runReadActionIgnoreDumbMode(() -> {
        final PsiMethod target = parameters.getMethod();
        final @Nullable String symbol = operatorName2operatorSymbol[target.getName()];
        if (symbol != null)
            lookupExpression(consumer, parameters.getProject(), parameters.getMethod(), parameters.getEffectiveSearchScope());
    });
    
    public static void lookupExpression(final Processor<PsiReference> consumer, final Project project, final PsiMethod method, final SearchScope searchScope) {
        if (searchScope instanceof final GlobalSearchScope globalSearchScope)
            JavaShortClassNameIndex.getInstance().getAllKeys(project).forEach(name -> JavaShortClassNameIndex.getInstance().get(name, project, globalSearchScope).forEach(psiClass -> lookupExpression(consumer, method, psiClass)));
        else if (searchScope instanceof final LocalSearchScope localSearchScope)
            Stream.of(localSearchScope.getScope()).forEach(element -> lookupExpression(consumer, method, element));
    }
    
    public static void lookupExpression(final Processor<PsiReference> consumer, final PsiMethod method, final PsiElement element) = PsiTreeUtil.findChildrenOfType(element, PsiExpression.class).stream()
            .map(expression -> PsiTreeUtil.getChildrenOfType(expression, PsiJavaToken.class))
            .filter(result -> result != null && result.length > 0)
            .flatMap(Stream::of)
            .map(PsiElement::getReference)
            .nonnull()
            .filter(reference -> reference.isReferenceTo(method))
            .forEach(consumer::process);
    
    public static @Nullable PsiExpression[] expressions(final PsiElement element) = switch (element) {
        case final PsiUnaryExpression unary           -> new PsiExpression[]{ unary.getOperand() };
        case final PsiBinaryExpression binary         -> new PsiExpression[]{ binary.getLOperand(), binary.getROperand() };
        case final PsiPolyadicExpression polyadic     -> polyadic.getOperands();
        case final PsiAssignmentExpression assignment -> new PsiExpression[]{ assignment.getLExpression(), assignment.getRExpression() };
        case final PsiArrayAccessExpression access    -> new PsiExpression[]{ access.getArrayExpression(), access.getIndexExpression() };
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
        if (element.getParent() instanceof final PsiExpression expression) {
            final @Nullable OverloadInfo info = expr(expression);
            if (info != null) {
                final PsiExpression expressions[] = info.expression.getArgumentList().getExpressions();
                final @Nullable PsiExpression operatorExpressions[] = expressions(expression);
                if (operatorExpressions != null) {
                    final int idx = lambdaIdx(operatorExpressions, element);
                    if (idx > -1 && (operatorExpressions.length > expressions.length ? idx == 0 ? info.expression.getMethodExpression().getQualifierExpression()
                            : expressions.length > idx - 1 ? expressions[idx - 1] : null : expressions.length > idx ? expressions[idx] : null) instanceof final PsiFunctionalExpression functionalExpression)
                        return { functionalExpression.getFunctionalInterfaceType() };
                }
            }
        }
        return Hook.Result.VOID;
    }
    
    // # LeafPsiElement
    
    @Hook
    private static Hook.Result getReference(final LeafPsiElement $this) = $this instanceof final PsiJavaTokenImpl token ?
            Hook.Result.nullToVoid(Optional.ofNullable(PsiTreeUtil.getContextOfType(token, PsiExpression.class))
                    .map(OperatorOverloadingHandler::expr)
                    .map(data -> data.reference.dup(token))
                    .orElse(null)) : Hook.Result.VOID;
    
    // # com.intellij.psi.controlFlow.ControlFlowAnalyzer
    
    public static Hook.Result getResult(final ControlFlowAnalyzer $this, final PsiExpression expression) {
        final @Nullable OverloadInfo data = expr(expression);
        if (data == null)
            return Hook.Result.VOID;
        (Privilege) $this.startElement(expression);
        for (final PsiExpression arg : data.args)
            arg.accept($this);
        (Privilege) $this.emitEmptyInstruction();
        (Privilege) $this.generateExceptionJumps(expression, ExceptionUtil.getUnhandledExceptions(data.expression, expression.getParent()));
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
        final @Nullable OverloadInfo data = expr(expression);
        if (data == null)
            return Hook.Result.VOID;
        (data.lower?.expression ?? data.expression).accept($this);
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
    
    // # PsiPrecedenceUtil
    
    @Hook(value = PsiPrecedenceUtil.class, isStatic = true)
    public static Hook.Result getPrecedence(final PsiExpression expression) = Hook.Result.falseToVoid(
            expression instanceof final PsiTypeCastExpression castExpression &&
            castExpression.getOperand() instanceof final PsiLambdaExpression lambdaExpression &&
            lambdaExpression.getBody() instanceof PsiExpression, PsiPrecedenceUtil.LAMBDA_PRECEDENCE);
    
    // # Checker
    
    @Hook
    private static Hook.Result visitPrefixExpression(final SimplifiableBooleanExpressionInspection.SimplifiableBooleanExpressionVisitor $this, final PsiPrefixExpression expression)
            = Hook.Result.falseToVoid(expr(expression) != null || expr(PsiUtil.skipParenthesizedExprDown(expression.getOperand())) != null, null);
    
    @Hook(value = ExpressionUtils.class, isStatic = true)
    private static Hook.Result isStringConcatenation(final PsiElement element) = Hook.Result.falseToVoid(element instanceof final PsiPolyadicExpression expression && expression.getOperationTokenType() != PLUS, false);
    
}
