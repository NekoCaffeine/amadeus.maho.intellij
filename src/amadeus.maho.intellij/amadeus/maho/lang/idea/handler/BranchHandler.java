package amadeus.maho.lang.idea.handler;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.LambdaCanBeMethodReferenceInspection;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaPolyadicPartAnchor;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.EvalInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfAntiConstantType;
import com.intellij.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.types.DfStreamStateType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.debugger.engine.evaluation.expression.Evaluator;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.BasicOldExpressionParser;
import com.intellij.lang.java.parser.BasicReferenceParser;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiPostfixExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiUnaryExpression;
import com.intellij.psi.formatter.java.JavaSpacePropertyProcessor;
import com.intellij.psi.impl.ConstantExpressionVisitor;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.java.PsiBinaryExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiPolyadicExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.refactoring.typeMigration.TypeEvaluator;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.idea.handler.base.BaseSyntaxHandler;
import amadeus.maho.lang.idea.handler.base.Syntax;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Proxy;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.EnumHelper;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.vm.tools.hotspot.WhiteBox;

import com.siyeh.ig.migration.UnnecessaryUnboxingInspection;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;

import static amadeus.maho.lang.idea.IDEAContext.AdditionalOperators.*;
import static amadeus.maho.lang.idea.handler.BranchHandler.PRIORITY;
import static amadeus.maho.util.bytecode.Bytecodes.NEW;
import static com.intellij.codeInspection.dataFlow.NullabilityProblemKind.*;
import static com.intellij.codeInspection.dataFlow.types.DfTypes.*;
import static com.intellij.psi.JavaTokenType.EXCL;
import static com.intellij.psi.util.TypeConversionUtil.*;

@TransformProvider
@Syntax(priority = PRIORITY)
public class BranchHandler extends BaseSyntaxHandler {
    
    public static final int PRIORITY = 1 << 4;
    
    @Hook(at = @At(field = @At.FieldInsn(name = "DOT"), ordinal = 0), before = false, capture = true)
    private static boolean parsePrimary(final boolean capture, final BasicOldExpressionParser $this, final PsiBuilder builder,
            final @Nullable BasicOldExpressionParser.BreakPoint breakPoint, final int breakOffset, final int mode) = capture || ACCESS_OPS.contains(builder.getTokenType());
    
    @Hook(at = @At(field = @At.FieldInsn(name = "DOT"), ordinal = 0), before = false, capture = true)
    private static boolean parseJavaCodeReference(final boolean capture, final BasicReferenceParser $this, final PsiBuilder builder, final boolean eatLastDot, final boolean parameterList,
            final boolean isImport, final boolean isStaticImport, final boolean isNew, final boolean diamonds, final BasicReferenceParser.TypeInfo typeInfo) = capture || ACCESS_OPS.contains(builder.getTokenType());
    
    private static final TokenSet NULL_OR_OPS = TokenSet.create(NULL_OR), ACCESS_OPS = TokenSet.create(SAFE_ACCESS, ASSERT_ACCESS);
    
    @Proxy(NEW)
    private static native BasicOldExpressionParser.ExprType newExprType(String name, int id);
    
    public static final int nextTypeId[] = { BasicOldExpressionParser.ExprType.values().length };
    
    private static final BasicOldExpressionParser.ExprType NULL_OR_TYPE = newExprType("NULL_OR", nextTypeId[0]++);
    
    static {
        EnumHelper.addEnum(NULL_OR_TYPE);
        final TokenSet NEW_POSTFIX_OPS = TokenSet.orSet((Privilege) BasicOldExpressionParser.POSTFIX_OPS, TokenSet.create(EXCL));
        final MethodHandles.Lookup lookup = MethodHandleHelper.lookup();
        try {
            lookup.findStaticVarHandle(BasicOldExpressionParser.class, "POSTFIX_OPS", TokenSet.class).set(NEW_POSTFIX_OPS);
            final Method parsePostfix = BasicOldExpressionParser.class.getDeclaredMethod("parsePostfix", PsiBuilder.class, int.class);
            final WhiteBox whiteBox = WhiteBox.instance();
            if (whiteBox.getMethodCompilationLevel(parsePostfix) > 0)
                whiteBox.deoptimizeMethod(parsePostfix);
        } catch (final ReflectiveOperationException e) { DebugHelper.breakpoint(e); }
    }
    
    @Hook(at = @At(field = @At.FieldInsn(name = "UNARY")), capture = true, before = false)
    private static BasicOldExpressionParser.ExprType parseExpression(final BasicOldExpressionParser.ExprType capture, final BasicOldExpressionParser $this,
            final PsiBuilder builder, final BasicOldExpressionParser.ExprType type, final int mode) = NULL_OR_TYPE;
    
    @Hook
    private static Hook.Result parseExpression(final BasicOldExpressionParser $this, final PsiBuilder builder, final BasicOldExpressionParser.ExprType type, final int mode)
            = type == NULL_OR_TYPE ? new Hook.Result((Privilege) $this.parseBinary(builder, BasicOldExpressionParser.ExprType.UNARY, NULL_OR_OPS, mode)) : Hook.Result.VOID;
    
    @Hook
    private static Hook.Result visitPolyadicExpression(final JavaSpacePropertyProcessor $this, final PsiPolyadicExpression expression) {
        if (expression instanceof CompositeElement element && element.findChildByRoleAsPsiElement(ChildRole.OPERATION_SIGN) instanceof PsiJavaToken token && token.getTokenType() == NULL_OR) {
            (Privilege) $this.createSpaceInCode(true);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static @Nullable ASTNode findChildByRole(final @Nullable ASTNode capture, final PsiPolyadicExpressionImpl $this, final int role)
            = capture == null && role == ChildRole.OPERATION_SIGN ? $this.findChildByType(NULL_OR_OPS) : capture;
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static int getChildRole(final int capture, final PsiPolyadicExpressionImpl $this, final ASTNode child)
            = capture == ChildRoleBase.NONE && NULL_OR_OPS.contains(child.getElementType()) ? ChildRole.OPERATION_SIGN : capture;
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static @Nullable ASTNode findChildByRole(final @Nullable ASTNode capture, final PsiBinaryExpressionImpl $this, final int role)
            = capture == null && role == ChildRole.OPERATION_SIGN ? $this.findChildByType(NULL_OR_OPS) : capture;
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static int getChildRole(final int capture, final PsiBinaryExpressionImpl $this, final ASTNode child)
            = capture == ChildRoleBase.NONE && NULL_OR_OPS.contains(child.getElementType()) ? ChildRole.OPERATION_SIGN : capture;
    
    @Hook(at = @At(method = @At.MethodInsn(name = "findChildByType")), before = false, capture = true)
    private static ASTNode deleteChildInternal(final ASTNode capture, final PsiReferenceExpressionImpl $this, final ASTNode child) = capture ?? $this.findChildByType(ACCESS_OPS);
    
    public static boolean isSafeAccess(final @Nullable PsiExpression expression) = switch (expression) {
        case PsiMethodCallExpression callExpression     -> callExpression.getMethodExpression() instanceof CompositeElement element && element.findPsiChildByType(SAFE_ACCESS) != null;
        case PsiReferenceExpression referenceExpression -> referenceExpression instanceof CompositeElement element && element.findPsiChildByType(SAFE_ACCESS) != null;
        case null,
             default                                    -> false;
    };
    
    public static boolean isAssertAccess(final @Nullable PsiExpression expression) = switch (expression) {
        case PsiMethodCallExpression callExpression     -> callExpression.getMethodExpression() instanceof CompositeElement element && element.findPsiChildByType(ASSERT_ACCESS) != null;
        case PsiReferenceExpression referenceExpression -> referenceExpression instanceof CompositeElement element && element.findPsiChildByType(ASSERT_ACCESS) != null;
        case null,
             default                                    -> false;
    };
    
    @Hook
    private static <T extends PsiElement> Hook.Result ifMyProblem(final NullabilityProblemKind<T> $this, final NullabilityProblemKind.NullabilityProblem<?> problem, final Consumer<? super T> consumer) {
        if ($this == callNPE || $this == fieldAccessNPE) {
            final NullabilityProblemKind.NullabilityProblem<T> myProblem = $this.asMyProblem(problem);
            if (myProblem != null && ($this == fieldAccessNPE ? myProblem.getAnchor().getParent() : myProblem.getAnchor()) instanceof PsiExpression expression && (isSafeAccess(expression) || isAssertAccess(expression)))
                return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkUnaryOperatorApplicable(final PsiJavaToken token, final @Nullable PsiExpression expr)
            = Hook.Result.falseToVoid(expr != null && expr.getParent() instanceof PsiPostfixExpression postfixExpression && postfixExpression.getOperationTokenType() == EXCL, null);
    
    @Hook(value = PsiTypesUtil.class, isStatic = true)
    private static Hook.Result getExpectedTypeByParent(final PsiElement element) {
        if (element instanceof PsiPostfixExpression postfixExpression && postfixExpression.getOperationTokenType() == EXCL)
            return { PsiTypesUtil.getExpectedTypeByParent(postfixExpression.getParent()) };
        if (element instanceof PsiLambdaExpression || element instanceof PsiMethodReferenceExpression) {
            @Nullable PsiElement parent = element.getParent(), current = element;
            while (parent != null) {
                if (parent instanceof PsiParenthesizedExpression) {
                    current = parent;
                    parent = parent.getParent();
                    continue;
                }
                if (parent instanceof PsiPolyadicExpression polyadicExpression && polyadicExpression.getOperationTokenType() == NULL_OR &&
                    polyadicExpression.getOperands().length > 0 && polyadicExpression.getOperands()[0] != current)
                    return { polyadicExpression.getOperands()[0].getType() };
                return Hook.Result.VOID;
            }
        }
        return Hook.Result.VOID;
    }
    
    @Hook(value = PsiUtil.class, isStatic = true)
    private static Hook.Result isStatement(final PsiElement element) = Hook.Result.falseToVoid(element instanceof PsiPostfixExpression postfixExpression && postfixExpression.getOperationTokenType() == EXCL);
    
    @Hook(value = LambdaUtil.class, isStatic = true)
    private static Hook.Result isExpressionStatementExpression(final PsiElement element) = isStatement(element);
    
    @Hook(value = LambdaUtil.class, isStatic = true)
    private static Hook.Result getFunctionalInterfaceType(final PsiElement expression, final boolean tryToSubstitute) = getExpectedTypeByParent(expression);
    
    @Hook(value = PsiPrecedenceUtil.class, isStatic = true)
    private static Hook.Result areParenthesesNeeded(final PsiExpression expression, final PsiExpression parentExpression, final boolean ignoreClarifyingParentheses)
            = Hook.Result.falseToVoid(expression instanceof PsiPolyadicExpression polyadicExpression && polyadicExpression.getOperationTokenType() == NULL_OR);
    
    @Hook
    private static Hook.Result isUnboxingNecessary(final UnnecessaryUnboxingInspection.UnnecessaryUnboxingVisitor $this, final PsiExpression expression, final PsiExpression unboxedExpression)
            = Hook.Result.falseToVoid(isSafeAccess(expression));
    
    @Hook(value = PsiPrecedenceUtil.class, isStatic = true)
    private static Hook.Result getPrecedenceForOperator(final IElementType operator)
            = Hook.Result.falseToVoid(operator == NULL_OR, PsiPrecedenceUtil.TYPE_CAST_PRECEDENCE);
    
    @Hook(value = PsiBinaryExpressionImpl.class, isStatic = true)
    private static Hook.Result doGetType(final PsiBinaryExpressionImpl expression)
            = expression.getOperationTokenType() == NULL_OR ? new Hook.Result(condType(expression, expression.getLOperand(), expression.getROperand())) : Hook.Result.VOID;
    
    @Hook(value = PsiPolyadicExpressionImpl.class, isStatic = true)
    private static Hook.Result doGetType(final PsiPolyadicExpressionImpl expression)
            = expression.getOperationTokenType() == NULL_OR ? new Hook.Result(condType(expression, expression.getOperands())) : Hook.Result.VOID;
    
    private static @Nullable PsiType condType(final PsiExpression owner, final Function<PsiExpression, PsiType> typeEvaluator = expression -> expression?.getType() ?? null, final PsiExpression... expressions) {
        if (expressions.length == 0)
            return null;
        final PsiType types[] = Stream.of(expressions).map(expression -> expression?.getType() ?? null).toArray(PsiType::createArray);
        if (Stream.of(types).distinct().count() == 1)
            return types[0];
        final PsiConstantEvaluationHelper evaluationHelper = JavaPsiFacade.getInstance(owner.getProject()).getConstantEvaluationHelper();
        final Object constValues[] = Stream.of(expressions).map(expression -> {
            final @Nullable PsiType type = typeEvaluator.apply(expression);
            if (type == null)
                return null;
            return getTypeRank(type) < LONG_RANK ? evaluationHelper.computeConstantExpression(expression) : null;
        }).toArray();
        if (PsiPolyExpressionUtil.isPolyExpression(owner)) {
            final @Nullable PsiType targetType = InferenceSession.getTargetType(owner);
            if (MethodCandidateInfo.isOverloadCheck())
                return targetType != null && Stream.of(types).allMatch(type -> type != null && targetType.isAssignableFrom(type)) ? targetType : null;
            if (targetType != null)
                return targetType;
        }
        @Nullable PsiType result = types[0];
        @Nullable Object constValue = constValues[0];
        for (int i = 1; i < expressions.length; i++) {
            result = lubType(owner, result, types[i], constValue, constValues[i]);
            constValue = mergeConstInt(constValue, constValues[0]);
        }
        return result;
    }
    
    private static @Nullable Object mergeConstInt(final @Nullable Object constValue1, final @Nullable Object constValue2)
            = constValue1 instanceof Integer integer1 ? constValue2 instanceof Integer integer2 ? (Integer) Math.max(integer1, integer2) : constValue2 instanceof Character integer2 ?
            (Integer) Math.max(integer1, integer2) : null : constValue1 instanceof Character integer1 ? constValue2 instanceof Integer integer2 ?
            (Integer) Math.max(integer1, integer2) : constValue2 instanceof Character integer2 ? (Integer) Math.max(integer1, integer2) : null : null;
    
    private static @Nullable PsiType lubType(final PsiExpression context, final @Nullable PsiType type1, final @Nullable PsiType type2, final @Nullable Object constValue1, final @Nullable Object constValue2) {
        if (Objects.equals(type1, type2))
            return type1;
        if (type1 == null)
            return type2;
        if (type2 == null)
            return type1;
        final int typeRank1 = getTypeRank(type1), typeRank2 = getTypeRank(type2);
        if (type1 instanceof PsiClassType && type2.equals(PsiPrimitiveType.getUnboxedType(type1)))
            return type2;
        if (type2 instanceof PsiClassType && type1.equals(PsiPrimitiveType.getUnboxedType(type2)))
            return type1;
        if (isNumericType(typeRank1) && isNumericType(typeRank2)) {
            if (typeRank1 == BYTE_RANK && typeRank2 == SHORT_RANK)
                return type2 instanceof PsiPrimitiveType ? type2 : PsiPrimitiveType.getUnboxedType(type2);
            if (typeRank1 == SHORT_RANK && typeRank2 == BYTE_RANK)
                return type1 instanceof PsiPrimitiveType ? type1 : PsiPrimitiveType.getUnboxedType(type1);
            if (typeRank2 == INT_RANK && (typeRank1 == BYTE_RANK || typeRank1 == SHORT_RANK || typeRank1 == CHAR_RANK))
                if (areTypesAssignmentCompatible(type1, type2, constValue2))
                    return type1;
            if (typeRank1 == INT_RANK && (typeRank2 == BYTE_RANK || typeRank2 == SHORT_RANK || typeRank2 == CHAR_RANK))
                if (areTypesAssignmentCompatible(type2, type1, constValue1))
                    return type2;
            return binaryNumericPromotion(type1, type2);
        }
        if (isNullType(type1) && !(type2 instanceof PsiPrimitiveType))
            return type2;
        if (isNullType(type2) && !(type1 instanceof PsiPrimitiveType))
            return type1;
        if (isAssignable(type1, type2, false))
            return type1;
        if (isAssignable(type2, type1, false))
            return type2;
        if (isPrimitiveAndNotNull(type1) && type1 instanceof PsiPrimitiveType primitiveType1 && primitiveType1.getBoxedType(context) == null)
            return null;
        if (isPrimitiveAndNotNull(type2) && type2 instanceof PsiPrimitiveType primitiveType2 && primitiveType2.getBoxedType(context) == null)
            return null;
        final @Nullable PsiType leastUpperBound = GenericsUtil.getLeastUpperBound(type1, type2, context.getManager());
        return leastUpperBound != null ? PsiUtil.captureToplevelWildcards(leastUpperBound, context) : null;
    }
    
    private static boolean areTypesAssignmentCompatible(final PsiType lType, final PsiType rType, final @Nullable Object constValue) {
        if (lType == null || rType == null)
            return true;
        if (isAssignable(lType, rType))
            return true;
        final PsiType unboxedLType = !(lType instanceof PsiPrimitiveType) ? PsiPrimitiveType.getUnboxedType(lType) : lType;
        if (unboxedLType == null)
            return false;
        final int rTypeRank = getTypeRank(rType);
        if (rType instanceof PsiPrimitiveType && rTypeRank < LONG_RANK) {
            final int value;
            if (constValue instanceof Number number)
                value = number.intValue();
            else if (constValue instanceof Character character)
                value = character;
            else
                return false;
            if (PsiTypes.byteType().equals(unboxedLType))
                return -128 <= value && value <= 127;
            else if (PsiTypes.shortType().equals(unboxedLType))
                return -32768 <= value && value <= 32767;
            else if (PsiTypes.charType().equals(unboxedLType))
                return 0 <= value && value <= 0xFFFF;
        }
        return false;
    }
    
    @Hook
    private static Hook.Result visitPolyadicExpression(final RedundantCastUtil.MyIsRedundantVisitor $this, final PsiPolyadicExpression expression) {
        if (expression.getOperationTokenType() == NULL_OR) {
            $this.visitExpression(expression);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook(value = LambdaCanBeMethodReferenceInspection.class, isStatic = true)
    private static Hook.Result extractMethodReferenceCandidateExpression(final PsiElement body) = Hook.Result.falseToVoid(body instanceof PsiMethodCallExpression expression && isSafeAccess(expression), null);
    
    @Hook(value = ExpressionUtils.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static boolean isNullLiteral(final boolean capture, final PsiExpression expression)
            = capture || expression instanceof PsiPolyadicExpression polyadicExpression && polyadicExpression.getOperationTokenType() == NULL_OR && Stream.of(polyadicExpression.getOperands()).allMatch(ExpressionUtils::isNullLiteral);
    
    @Hook(value = ExpressionUtils.class, isStatic = true)
    private static Hook.Result isConversionToStringNecessary(final PsiExpression expression, final boolean throwable, final @Nullable PsiType type) {
        final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
        return Hook.Result.falseToVoid(parent instanceof PsiPolyadicExpression polyadicExpression && polyadicExpression.getOperationTokenType() == NULL_OR);
    }
    
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class NullOrInstruction extends EvalInstruction {
        
        PsiType targetType;
        
        public NullOrInstruction(final @Nullable DfaAnchor anchor, final PsiType targetType) {
            super(anchor, 2);
            this.targetType = targetType;
        }
        
        @Override
        public DfaValue eval(final DfaValueFactory factory, final DfaMemoryState state, final DfaValue... arguments) = switch (state.getDfType(arguments[0])) {
            case DfReferenceType referenceType  -> switch (referenceType.getNullability()) {
                case NULL     -> arguments[1];
                case NOT_NULL -> arguments[0];
                case NULLABLE -> state.getDfType(arguments[1]) instanceof DfReferenceType otherType && otherType.getNullability() != DfaNullability.NULL ?
                        factory.fromDfType(typedObject(targetType, DfaNullability.toNullability(otherType.getNullability()))) : factory.fromDfType(typedObject(targetType, Nullability.NULLABLE));
                default       -> factory.fromDfType(typedObject(targetType, Nullability.UNKNOWN));
            };
            case DfConstantType<?> constantType -> constantType.getValue() != null ? arguments[0] : arguments[1];
            case DfAntiConstantType<?> ignored  -> arguments[0];
            case DfStreamStateType ignored      -> arguments[0];
            default                             -> factory.fromDfType(typedObject(targetType, Nullability.UNKNOWN));
        };
        
        public String toString() = "NULL_OR";
        
    }
    
    @Hook
    private static Hook.Result generateBinOpChain(final ControlFlowAnalyzer $this, final PsiPolyadicExpression expression, final IElementType op, final PsiExpression[] operands) {
        if (op == NULL_OR) {
            operands[0].accept($this);
            for (int i = 1; i < operands.length; i++) {
                operands[i].accept($this);
                (Privilege) $this.addInstruction(new NullOrInstruction(i == operands.length - 1 ? new JavaExpressionAnchor(expression) : new JavaPolyadicPartAnchor(expression, i), expression.getType() ?? PsiTypes.nullType()));
                (Privilege) $this.addNullCheck(expression);
            }
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    public static class AssertNonNullInstruction extends Instruction {
        
        @Override
        public DfaInstructionState[] accept(final DataFlowInterpreter interpreter, final DfaMemoryState memState) {
            memState.updateDfType(memState.peek(), dfaType -> dfaType instanceof DfReferenceType referenceType ? referenceType.dropNullability().meet(NOT_NULL_OBJECT) : dfaType);
            return nextStates(interpreter, memState);
        }
        
        public String toString() = "ASSERT_NON_NULL";
        
    }
    
    @Hook
    private static Hook.Result processIncrementDecrement(final ControlFlowAnalyzer $this, final PsiUnaryExpression expression, final PsiExpression operand, final boolean postIncrement) {
        if (postIncrement && expression.getOperationTokenType() == EXCL) {
            operand.accept($this);
            if (operand.getType() instanceof PsiPrimitiveType primitiveType)
                (Privilege) $this.generateBoxingUnboxingInstructionFor(operand, primitiveType.getBoxedType(operand));
            (Privilege) $this.addInstruction(new AssertNonNullInstruction());
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkPolyadicOperatorApplicable(final PsiPolyadicExpression expression) = Hook.Result.falseToVoid(expression.getOperationTokenType() == NULL_OR, null);
    
    @Hook
    private static Hook.Result evaluateType(final TypeEvaluator $this, final PsiExpression expr) {
        if (expr instanceof PsiPolyadicExpression polyadicExpression && polyadicExpression.getOperationTokenType() == NULL_OR)
            return { condType(polyadicExpression, $this::evaluateType, polyadicExpression.getOperands()) };
        if (expr instanceof PsiPostfixExpression postfixExpression && postfixExpression.getOperationTokenType() == EXCL)
            return { $this.evaluateType(postfixExpression.getOperand()) };
        return Hook.Result.VOID;
    }
    
    @Hook(value = EvaluatorBuilderImpl.Builder.class, isStatic = true)
    private static Hook.Result createBinaryEvaluator(final Evaluator lResult, final PsiType lType, final Evaluator rResult, final PsiType rType, final IElementType operation, final PsiType expressionExpectedType) {
        if (operation == NULL_OR) {
            final Evaluator evaluator = context -> lResult.evaluate(context) ?? rResult.evaluate(context);
            return { evaluator };
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result visitPostfixExpression(final EvaluatorBuilderImpl.Builder $this, final PsiPostfixExpression expression) {
        if (expression.getOperationTokenType() == EXCL) {
            expression.getOperand().accept($this);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result visitPolyadicExpression(final ConstantExpressionVisitor $this, final PsiPolyadicExpression expression) {
        final IElementType tokenType = expression.getOperationTokenType();
        if (tokenType == NULL_OR && !(expression.getType() instanceof PsiPrimitiveType)) {
            final PsiExpression operands[] = expression.getOperands();
            @Nullable Object value = (Privilege) $this.getStoredValue(operands[0]);
            if (value != null || operands[0] instanceof PsiLiteral)
                for (int i = 1; i < operands.length; i++) {
                    final PsiExpression operand = operands[i];
                    value = value ?? (Privilege) $this.getStoredValue(operand);
                    if (value == null && !(operands[0] instanceof PsiLiteral))
                        break;
                }
            if (value instanceof String string)
                value = ((Privilege) $this.myInterner).intern(string);
            // noinspection DataFlowIssue
            (Privilege) ($this.myResult = value);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Override
    public void check(final PsiElement tree, final ProblemsHolder holder, final QuickFixFactory quickFix, final boolean isOnTheFly) {
        if (tree instanceof PsiReferenceExpression expression && isSafeAccess(expression)) {
            @Nullable PsiElement parent = expression.getParent(), current = expression;
            while (parent != null) {
                if (!(parent instanceof PsiReferenceExpression || parent instanceof PsiMethodCallExpression callExpression && callExpression.getMethodExpression() == current || parent instanceof PsiTypeCastExpression)) {
                    if (parent instanceof PsiExpressionStatement || parent instanceof PsiLambdaExpression lambdaExpression && lambdaExpression.getBody() == current && lambdaExpression.isVoidCompatible() ||
                        parent instanceof PsiReturnStatement returnStatement && PsiTypes.voidType().equals(PsiTreeUtil.getParentOfType(returnStatement, PsiMethod.class)?.getReturnType() ?? null) ||
                        parent instanceof PsiPolyadicExpression polyadicExpression && polyadicExpression.getOperationTokenType() == NULL_OR &&
                        polyadicExpression.getOperands().length > 1 && polyadicExpression.getOperands()[polyadicExpression.getOperands().length - 1] != current)
                        return;
                    holder.registerProblem(expression.getElement(), "?. expressions are not allowed here, they must be in the context of a ?? expression", ProblemHighlightType.GENERIC_ERROR);
                    return;
                }
                current = parent;
                parent = parent.getParent();
            }
        }
    }
    
}
