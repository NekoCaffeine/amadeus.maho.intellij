package amadeus.maho.lang.idea.handler;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaPolyadicPartAnchor;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.EvalInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfAntiConstantType;
import com.intellij.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.types.DfStreamStateType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.OldExpressionParser;
import com.intellij.lang.java.parser.ReferenceParser;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.formatter.java.JavaSpacePropertyProcessor;
import com.intellij.psi.impl.ConstantExpressionVisitor;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.java.PsiBinaryExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiPolyadicExpressionImpl;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.refactoring.typeMigration.TypeEvaluator;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Proxy;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.EnumHelper;

import com.siyeh.ig.migration.UnnecessaryUnboxingInspection;

import static amadeus.maho.lang.idea.IDEAContext.AdditionalOperators.*;
import static amadeus.maho.lang.idea.IDEAContext.createSpaceInCode;
import static amadeus.maho.util.bytecode.Bytecodes.*;
import static com.intellij.codeInspection.dataFlow.NullabilityProblemKind.*;
import static com.intellij.codeInspection.dataFlow.types.DfTypes.typedObject;
import static com.intellij.psi.util.TypeConversionUtil.*;

@TransformProvider
public class BranchHandler {
    
    @Hook(at = @At(field = @At.FieldInsn(name = "DOT"), ordinal = 0), before = false, capture = true)
    private static boolean parsePrimary(final boolean capture, final OldExpressionParser $this, final PsiBuilder builder, final @Nullable OldExpressionParser.BreakPoint breakPoint, final int breakOffset, final int mode)
            = capture || builder.getTokenType() == SAFE_ACCESS;
    
    @Hook(at = @At(field = @At.FieldInsn(name = "DOT"), ordinal = 0), before = false, capture = true)
    private static boolean parseJavaCodeReference(final boolean capture, final ReferenceParser $this, final PsiBuilder builder, final boolean eatLastDot, final boolean parameterList, final boolean isImport, final boolean isStaticImport,
            final boolean isNew, final boolean diamonds, final ReferenceParser.TypeInfo typeInfo) = capture || builder.getTokenType() == SAFE_ACCESS;
    
    private static final TokenSet NULL_OR_OPS = TokenSet.create(NULL_OR);
    
    @Proxy(NEW)
    private static native OldExpressionParser.ExprType newExprType(String name, int id);
    
    @Proxy(value = INVOKESTATIC, targetClass = OldExpressionParser.ExprType.class)
    private static native OldExpressionParser.ExprType[] values();
    
    private static final OldExpressionParser.ExprType NULL_OR_TYPE = newExprType("NULL_OR", values().length);
    
    static { EnumHelper.addEnum(NULL_OR_TYPE); }
    
    @Hook(at = @At(field = @At.FieldInsn(name = "UNARY")), capture = true, before = false)
    private static OldExpressionParser.ExprType parseExpression(final OldExpressionParser.ExprType capture, final OldExpressionParser $this, final PsiBuilder builder, final OldExpressionParser.ExprType type, final int mode) = NULL_OR_TYPE;
    
    @Hook
    private static Hook.Result parseExpression(final OldExpressionParser $this, final PsiBuilder builder, final OldExpressionParser.ExprType type, final int mode)
            = type == NULL_OR_TYPE ? new Hook.Result((Privilege) $this.parseBinary(builder, OldExpressionParser.ExprType.UNARY, NULL_OR_OPS, mode)) : Hook.Result.VOID;
    
    @Hook
    private static Hook.Result visitPolyadicExpression(final JavaSpacePropertyProcessor $this, final PsiPolyadicExpression expression) {
        if (expression.getOperationTokenType() == NULL_OR) {
            createSpaceInCode($this, true);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    public static boolean isSafeAccess(final @Nullable PsiExpression expression) = switch (expression) {
        case final PsiMethodCallExpression callExpression     -> callExpression.getMethodExpression() instanceof final CompositeElement element && element.findPsiChildByType(SAFE_ACCESS) != null;
        case final PsiReferenceExpression referenceExpression -> referenceExpression instanceof final CompositeElement element && element.findPsiChildByType(SAFE_ACCESS) != null;
        case null, default                                    -> false;
    };
    
    @Hook
    public static <T extends PsiElement> Hook.Result ifMyProblem(final NullabilityProblemKind<T> $this, final NullabilityProblemKind.NullabilityProblem<?> problem, final Consumer<? super T> consumer) {
        if ($this == callNPE || $this == fieldAccessNPE) {
            final NullabilityProblemKind.NullabilityProblem<T> myProblem = $this.asMyProblem(problem);
            if (myProblem != null && isSafeAccess((PsiExpression) ($this == fieldAccessNPE ? myProblem.getAnchor().getParent() : myProblem.getAnchor())))
                return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result isUnboxingNecessary(final UnnecessaryUnboxingInspection.UnnecessaryUnboxingVisitor $this, final PsiExpression expression, final PsiExpression unboxedExpression)
            = Hook.Result.falseToVoid(isSafeAccess(expression));
    
    @Hook(value = PsiPrecedenceUtil.class, isStatic = true)
    private static Hook.Result getPrecedenceForOperator(final IElementType operator)
            = Hook.Result.falseToVoid(operator == NULL_OR, PsiPrecedenceUtil.TYPE_CAST_PRECEDENCE);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static @Nullable ASTNode findChildByRole(final @Nullable ASTNode capture, final PsiBinaryExpressionImpl $this, final int role)
            = role == ChildRole.OPERATION_SIGN && capture == null ? $this.findChildByType(NULL_OR) : capture;
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static int getChildRole(final int capture, final PsiBinaryExpressionImpl $this, final ASTNode child)
            = child.getElementType() == NULL_OR && capture == ChildRoleBase.NONE ? ChildRole.OPERATION_SIGN : capture;
    
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
    
    private static @Nullable Object mergeConstInt(final @Nullable Object constValue1, final @Nullable Object constValue2) =
    constValue1 instanceof final Integer integer1 ? constValue2 instanceof final Integer integer2 ? (Integer) Math.max(integer1, integer2) : constValue2 instanceof final Character integer2 ? (Integer) Math.max(integer1, integer2) : null : constValue1 instanceof final Character integer1 ? constValue2 instanceof final Integer integer2 ? (Integer) Math.max(integer1, integer2) : constValue2 instanceof final Character integer2 ? (Integer) Math.max(integer1, integer2) : null : null;
    
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
        @Nullable PsiType boxedType1 = null, boxedType2 = null;
        if (isPrimitiveAndNotNull(type1) && type1 instanceof final PsiPrimitiveType primitiveType1 && (boxedType1 = primitiveType1.getBoxedType(context)) == null)
            return null;
        if (isPrimitiveAndNotNull(type2) && type2 instanceof final PsiPrimitiveType primitiveType2 && (boxedType2 = primitiveType2.getBoxedType(context)) == null)
            return null;
        final @Nullable PsiType leastUpperBound = GenericsUtil.getLeastUpperBound(boxedType1, boxedType2, context.getManager());
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
            if (constValue instanceof final Number number)
                value = number.intValue();
            else if (constValue instanceof final Character character)
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
            case final DfReferenceType referenceType  -> switch (referenceType.getNullability()) {
                case NULL     -> arguments[1];
                case NOT_NULL -> arguments[0];
                case NULLABLE -> state.getDfType(arguments[1]) instanceof final DfReferenceType otherType && otherType.getNullability() != DfaNullability.NULL ?
                        factory.fromDfType(typedObject(targetType, DfaNullability.toNullability(otherType.getNullability()))) : factory.fromDfType(typedObject(targetType, Nullability.NULLABLE));
                default       -> factory.fromDfType(typedObject(targetType, Nullability.UNKNOWN));
            };
            case final DfConstantType<?> constantType -> constantType.getValue() != null ? arguments[0] : arguments[1];
            case final DfAntiConstantType<?> ignored  -> arguments[0];
            case final DfStreamStateType ignored      -> arguments[0];
            default                                   -> factory.fromDfType(typedObject(targetType, Nullability.UNKNOWN));
        };
        
        public String toString() = "NULL_OR";
        
    }
    
    @Hook
    private static Hook.Result generateBinOpChain(final ControlFlowAnalyzer $this, final PsiPolyadicExpression expression, final IElementType op, final PsiExpression[] operands) {
        if (op == NULL_OR) {
            generateBinOpChain($this, expression, operands);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    private static void generateBinOpChain(final ControlFlowAnalyzer $this, final PsiPolyadicExpression expression, final PsiExpression[] operands) {
        operands[0].accept($this);
        for (int i = 1; i < operands.length; i++) {
            operands[i].accept($this);
            (Privilege) $this.addInstruction(new NullOrInstruction(i == operands.length - 1 ? new JavaExpressionAnchor(expression) : new JavaPolyadicPartAnchor(expression, i), expression.getType() ?? PsiTypes.nullType()));
        }
    }
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkPolyadicOperatorApplicable(final PsiPolyadicExpression expression) = Hook.Result.falseToVoid(expression.getOperationTokenType() == NULL_OR, null);
    
    @Hook
    private static Hook.Result evaluateType(final TypeEvaluator $this, final PsiExpression expr) {
        if (expr instanceof final PsiPolyadicExpression polyadicExpression && polyadicExpression.getOperationTokenType() == NULL_OR)
            return { condType(polyadicExpression, $this::evaluateType, polyadicExpression.getOperands()) };
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result visitPolyadicExpression(final ConstantExpressionVisitor $this, final PsiPolyadicExpression expression) {
        final IElementType tokenType = expression.getOperationTokenType();
        if (tokenType == NULL_OR && !(expression.getType() instanceof PsiPrimitiveType)) {
            final PsiExpression operands[] = expression.getOperands();
            @Nullable Object value = (Privilege) $this.getStoredValue(operands[0]);
            for (int i = 1; i < operands.length; i++) {
                final PsiExpression operand = operands[i];
                value = value ?? (Privilege) $this.getStoredValue(operand);
            }
            if (value instanceof final String string)
                value = ((Privilege) $this.myInterner).intern(string);
            (Privilege) ($this.myResult = value);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
}
