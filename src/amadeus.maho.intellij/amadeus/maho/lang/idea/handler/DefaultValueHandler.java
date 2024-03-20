package amadeus.maho.lang.idea.handler;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightClassUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderUtil;
import com.intellij.lang.java.parser.BasicDeclarationParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiParameterListOwner;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiRecordHeader;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.ResolveState;
import com.intellij.psi.formatter.java.JavaSpacePropertyProcessor;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;

import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.idea.handler.base.BaseSyntaxHandler;
import amadeus.maho.lang.idea.handler.base.ExtensibleMembers;
import amadeus.maho.lang.idea.handler.base.HandlerSupport;
import amadeus.maho.lang.idea.handler.base.Syntax;
import amadeus.maho.lang.idea.light.LightDefaultParameter;
import amadeus.maho.lang.idea.light.LightElement;
import amadeus.maho.lang.idea.light.LightMethod;
import amadeus.maho.lang.idea.light.LightModifierList;
import amadeus.maho.lang.idea.light.LightParameter;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.tuple.Tuple;
import amadeus.maho.util.tuple.Tuple2;

import static amadeus.maho.lang.idea.IDEAContext.*;
import static amadeus.maho.lang.idea.handler.DefaultValueHandler.PRIORITY;
import static amadeus.maho.util.bytecode.Bytecodes.INSTANCEOF;
import static com.intellij.psi.JavaTokenType.*;

@TransformProvider
@Syntax(priority = PRIORITY)
public class DefaultValueHandler extends BaseSyntaxHandler {
    
    public static final int PRIORITY = ConstructorHandler.PRIORITY << 1;
    
    @NoArgsConstructor
    public static class DerivedMethod extends LightMethod { }
    
    public static List<DerivedMethod> derivedMethods(final PsiMethod method) = CachedValuesManager.getProjectPsiDependentCache(method, it -> {
        if (!(it instanceof DerivedMethod)) {
            final @Nullable PsiType returnType = it.getReturnType();
            final LinkedList<Tuple2<PsiParameter, PsiExpression>> defaultValues = { };
            Stream.of(it.getParameterList().getParameters())
                    .map(parameter -> Tuple.tuple(parameter, defaultValue(parameter)))
                    .filter(tuple -> tuple.v2 != null)
                    .forEach(defaultValues::addFirst);
            if (!defaultValues.isEmpty()) {
                final @Nullable PsiClass context = it.getContainingClass();
                return Stream.concat(Stream.of(new ArrayList<>(defaultValues)), Stream.generate(() -> defaultValues)
                                .limit(defaultValues.size() - 1)
                                .peek(LinkedList::removeLast)
                                .map(ArrayList::new))
                        .map(list -> {
                            final List<PsiParameter> parameters = new ArrayList<>(List.of(it.getParameterList().getParameters()));
                            parameters.removeIf(parameter -> list.stream().anyMatch(tuple -> tuple.v1 == parameter || tuple.v1.equals(parameter)));
                            final DerivedMethod derivedBridgeMethod = { context, it.getName(), it };
                            derivedBridgeMethod.mark("Derived");
                            if (returnType != null)
                                derivedBridgeMethod.setMethodReturnType(returnType);
                            Stream.of(it.getThrowsList().getReferencedTypes()).forEach(derivedBridgeMethod.getThrowsList()::addReference);
                            parameters.forEach(parameter -> {
                                final LightParameter lightParameter = { derivedBridgeMethod, parameter.getName(), parameter.getType(), parameter.isVarArgs() };
                                followAnnotation(parameter.getModifierList(), lightParameter.getModifierList());
                                derivedBridgeMethod.addParameter(lightParameter);
                            });
                            derivedBridgeMethod.setContainingClass(context);
                            derivedBridgeMethod.setNavigationElement(it);
                            derivedBridgeMethod.setConstructor(it.isConstructor());
                            derivedBridgeMethod.fieldInitialized(true);
                            final LightModifierList modifierList = derivedBridgeMethod.getModifierList();
                            followModifier(it, modifierList);
                            modifierList.annotations().removeIf(annotation -> {
                                final String name = annotation.getQualifiedName();
                                return Override.class.getCanonicalName().equals(name) || SafeVarargs.class.getCanonicalName().equals(name);
                            });
                            final Set<String> modifiers = modifierList.modifiers();
                            modifiers.remove(PsiModifier.ABSTRACT);
                            if (context.isInterface() && !modifiers.contains(PsiModifier.STATIC))
                                modifiers.add(PsiModifier.DEFAULT);
                            Stream.of(it.getTypeParameters()).forEach(derivedBridgeMethod::addTypeParameter);
                            Stream.of(it.getThrowsList().getReferencedTypes()).forEach(derivedBridgeMethod::addException);
                            return derivedBridgeMethod;
                        })
                        .toList();
            }
        }
        return List.of();
    });
    
    @Override
    public void processMethod(final PsiMethod tree, final ExtensibleMembers members, final PsiClass context) = derivedMethods(tree).forEach(members::inject);
    
    @Override
    public void collectRelatedTarget(final PsiModifierListOwner tree, final Set<PsiNameIdentifierOwner> targets) {
        if (tree instanceof PsiMethodImpl method)
            targets *= derivedMethods(method);
    }
    
    // Use the assigned setting when formatting the equals of the default value
    @Hook
    private static Hook.Result visitParameterList(final JavaSpacePropertyProcessor $this, final PsiParameterList list) {
        if ((Privilege) $this.myType1 == EQ || (Privilege) $this.myType2 == EQ) {
            (Privilege) $this.createSpaceInCode(((Privilege) $this.mySettings).SPACE_AROUND_ASSIGNMENT_OPERATORS);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result visitRecordHeader(final JavaSpacePropertyProcessor $this, final PsiRecordHeader header) {
        if ((Privilege) $this.myType1 == EQ || (Privilege) $this.myType2 == EQ) {
            (Privilege) $this.createSpaceInCode(((Privilege) $this.mySettings).SPACE_AROUND_ASSIGNMENT_OPERATORS);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    // The default value after trying to parse the parameter.
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static PsiBuilder.Marker parseParameterOrRecordComponent(final PsiBuilder.Marker capture, final BasicDeclarationParser $this, final PsiBuilder builder, final boolean ellipsis, final boolean disjunctiveType,
            final boolean varType, final boolean isParameter) {
        if (PsiBuilderUtil.expect(builder, EQ)) {
            final PsiBuilder.Marker marker = ((Privilege) $this.myParser).getExpressionParser().parse(builder);
            if (marker != null)
                return marker;
            JavaParserUtil.error(builder, JavaErrorBundle.message("expected.expression"));
        }
        return capture;
    }
    
    // Allow lambda in default value
    @Hook(value = LambdaUtil.class, isStatic = true)
    private static Hook.Result isValidLambdaContext(final PsiElement element) = Hook.Result.falseToVoid(element instanceof PsiParameterList || element instanceof PsiRecordHeader);
    
    // Infer the type of lambda from the corresponding parameter, eg: (Function<Object, Object> mapper = it -> it)
    @Hook(value = LambdaUtil.class, isStatic = true)
    private static Hook.Result getFunctionalInterfaceType(final PsiElement expression, final boolean tryToSubstitute) {
        if (expression.getParent() instanceof PsiParameterList) {
            PsiElement element = expression;
            while ((element = element.getPrevSibling()) != null) {
                if (element instanceof PsiParameter parameter)
                    return { parameter.getType() };
            }
        }
        if (expression.getParent() instanceof PsiRecordHeader) {
            PsiElement element = expression;
            while ((element = element.getPrevSibling()) != null) {
                if (element instanceof PsiRecordComponent recordComponent)
                    return { recordComponent.getType() };
            }
        }
        return Hook.Result.VOID;
    }
    
    // Allow default values to access other method parameters, eg: (Object obj, String str = obj.toString())
    @Hook(value = PsiImplUtil.class, isStatic = true, at = @At(type = @At.TypeInsn(opcode = INSTANCEOF, type = PsiCodeBlock.class)), capture = true, before = false)
    private static Hook.Result processDeclarationsInMethod(final boolean capture, final PsiMethod method, final PsiScopeProcessor processor, final ResolveState state, final @Nullable PsiElement lastParent, final PsiElement place) {
        if (!capture && lastParent instanceof PsiParameterList)
            return processDeclarationsInMethod(method, processor, state, place, method.getTypeParameterList()) ? Hook.Result.TRUE : Hook.Result.FALSE;
        return Hook.Result.VOID;
    }
    
    public static boolean processDeclarationsInMethod(final PsiParameterListOwner element, final PsiScopeProcessor processor, final ResolveState state, final PsiElement place, final @Nullable PsiTypeParameterList typeParameterList) {
        processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, element);
        if (typeParameterList != null) {
            final @Nullable ElementClassHint hint = processor.getHint(ElementClassHint.KEY);
            if (hint == null || hint.shouldProcess(ElementClassHint.DeclarationKind.CLASS))
                if (!typeParameterList.processDeclarations(processor, state, null, place))
                    return false;
        }
        // Only allow access to parameters without default values, because parameters with default values may lead to loop analysis and cause confusion.
        final PsiParameter parameters[] = element.getParameterList().getParameters();
        final int index = indexPlace(place), p_index[] = { -1 };
        return Stream.of(parameters).noneMatch(parameter -> (++p_index[0] <= index || defaultValue(parameter) == null) && !processor.execute(parameter, state));
    }
    
    @Hook(isStatic = true, value = PsiClassImplUtil.class)
    private static Hook.Result processDeclarationsInClass(final PsiClass context, @Hook.Reference PsiScopeProcessor processor, final ResolveState state, @Nullable final Set<PsiClass> visited, final PsiElement last,
            final PsiElement place, final LanguageLevel languageLevel, final boolean isRaw, final GlobalSearchScope resolveScope) {
        if (last instanceof PsiRecordHeader recordHeader) {
            final PsiScopeProcessor source = processor;
            processor = new PsiScopeProcessor() {
                
                @Override
                public boolean execute(final PsiElement element, final ResolveState state) {
                    if (element instanceof PsiField field) {
                        if (Stream.of(recordHeader.getRecordComponents())
                                .filter(component -> component.getName().equals(field.getName()))
                                .anyMatch(component -> defaultValue(component) != null))
                            return true;
                    }
                    return source.execute(element, state);
                }
                
                @Override
                public void handleEvent(final Event event, @Nullable final Object associated) = source.handleEvent(event, associated);
                
                @Override
                public <T> @Nullable T getHint(final Key<T> hintKey) = source.getHint(hintKey);
                
            };
            return { };
        }
        return Hook.Result.VOID;
    }
    
    // Make sure the field initialization check is correct
    @Hook(value = HighlightControlFlowUtil.class, isStatic = true, at = @At(method = @At.MethodInsn(name = "getConstructors")), capture = true, before = false)
    public static PsiMethod[] isFieldInitializedAfterObjectConstruction(final PsiMethod capture[], final PsiField field) = Stream.of(capture)
            .map(method -> method instanceof LightElement lightElement ? lightElement.equivalents().stream()
                    .filter(PsiMethod.class::isInstance)
                    .map(PsiMethod.class::cast)
                    .findFirst().orElse(method) : method)
            .distinct()
            .toArray(PsiMethod.ARRAY_FACTORY::create);
    
    private static void checkVariableDefaultValue(final PsiVariable variable, final Consumer<? super HighlightInfo.Builder> errorSink) {
        checkVariableDefaultValueType(variable).forEach(errorSink);
        checkVariableDefaultValueNotInitialized(variable).forEach(errorSink);
    }
    
    // Type check the default values
    @Hook
    private static void visitParameter(final HighlightVisitorImpl $this, final PsiParameter parameter) = checkVariableDefaultValue(parameter, (Privilege) $this.myErrorSink);
    
    @Hook
    private static void visitRecordComponent(final HighlightVisitorImpl $this, final PsiRecordComponent recordComponent) = checkVariableDefaultValue(recordComponent, (Privilege) $this.myErrorSink);
    
    public static @Nullable PsiVariable recordComponentForParameter(final PsiParameter parameter) {
        final @Nullable PsiMethod context = PsiTreeUtil.getContextOfType(parameter, PsiMethod.class);
        return context != null && context.isConstructor() ? recordComponentForParameter(context.getContainingClass(), parameter) : parameter;
    }
    
    private static @Nullable PsiRecordComponent recordComponentForParameter(final @Nullable PsiClass containingClass, final PsiParameter parameter) {
        if (containingClass == null || !containingClass.isRecord())
            return null;
        final String name = parameter.getName();
        return Stream.of(containingClass.getRecordComponents())
                .filter(component -> name.equals(component.getName()))
                .findFirst()
                .orElse(null);
    }
    
    public static @Nullable PsiExpression defaultValue(final PsiVariable variable) {
        if (variable instanceof LightDefaultParameter parameter)
            return parameter.defaultValue();
        PsiElement element = variable instanceof PsiParameter parameter ? recordComponentForParameter(parameter) ?? variable : variable;
        boolean innerEQ = false;
        while ((element = element.getNextSibling()) != null)
            if (element instanceof PsiJavaToken token) {
                final IElementType elementType = token.getTokenType();
                if (elementType == COMMA || elementType == RPARENTH)
                    break;
                if (elementType == EQ)
                    innerEQ = true;
            } else if (innerEQ && element instanceof PsiExpression expression)
                return expression;
        return null;
    }
    
    public static @Nullable PsiVariable defaultVariable(final PsiExpression expression) {
        boolean innerEQ = false;
        PsiElement element = expression;
        while ((element = element.getPrevSibling()) != null)
            if (element instanceof PsiJavaToken token) {
                final IElementType elementType = token.getTokenType();
                if (elementType == COMMA || elementType == LPARENTH)
                    break;
                if (elementType == EQ)
                    innerEQ = true;
            } else if (innerEQ && element instanceof PsiVariable variable)
                return variable;
        return null;
    }
    
    public static int indexPlace(final PsiElement element) {
        @Nullable PsiElement parent = element, child = element;
        while (parent != null) {
            if ((parent = parent.getContext()) instanceof PsiParameterList parameterList)
                return child instanceof PsiExpression expression ? ArrayHelper.indexOf(parameterList.getParameters(), defaultVariable(expression)) : -1;
            child = parent;
        }
        return -1;
    }
    
    @Hook(value = PsiPolyExpressionUtil.class, isStatic = true)
    private static Hook.Result isAssignmentContext(final PsiExpression expr, final PsiElement context) = Hook.Result.falseToVoid(defaultVariable(expr) != null);
    
    @Hook(value = PsiTypesUtil.class, isStatic = true)
    private static Hook.Result getExpectedTypeByParent(final PsiElement element) {
        if (element instanceof PsiExpression expression) {
            final @Nullable PsiVariable variable = defaultVariable(expression);
            if (variable instanceof PsiParameter || variable instanceof PsiRecordComponent)
                return Hook.Result.nullToVoid(variable.getType());
        }
        return Hook.Result.VOID;
    }
    
    // Allow default values to use arrays to directly assign expressions, eg: (String[] array = { "foo", "bar" })
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkArrayInitializerApplicable(final PsiArrayInitializerExpression expression)
    = Hook.Result.falseToVoid(expression.getParent() instanceof PsiParameterList || expression.getParent() instanceof PsiRecordHeader, null);
    
    private static Stream<HighlightInfo.Builder> checkVariableDefaultValueType(final PsiVariable variable) {
        final @Nullable PsiExpression expression = defaultValue(variable);
        if (expression != null) {
            if (expression instanceof PsiArrayInitializerExpression initializer && AssignHandler.type(initializer) instanceof PsiNewExpression)
                return Stream.empty();
            final @Nullable PsiType type = variable.getType(), expressionType = expression.getType();
            if (type instanceof PsiArrayType arrayType)
                if (expressionType == null)
                    if (expression instanceof PsiArrayInitializerExpression initializer)
                        return Stream.of(initializer.getInitializers())
                                .map(expr -> (Privilege) HighlightUtil.checkAssignability(arrayType.getComponentType(), expr.getType(), expr, expression.getTextRange(), 0))
                                .nonnull();
                    else
                        return Stream.empty();
                else if (TypeConversionUtil.isAssignable(arrayType.getComponentType(), expressionType))
                    return Stream.empty();
            final @Nullable HighlightInfo.Builder info = (Privilege) HighlightUtil.checkAssignability(type, expressionType, expression, variable.getTextRange().union(expression.getTextRange()), 0);
            if (info == null)
                return Stream.empty();
            final PsiType unpackedType = HandlerSupport.unwrapType(variable);
            if (unpackedType instanceof PsiArrayType arrayType)
                if (expressionType == null)
                    if (expression instanceof PsiArrayInitializerExpression initializer)
                        return Stream.of(initializer.getInitializers())
                                .map(expr -> (Privilege) HighlightUtil.checkAssignability(arrayType.getComponentType(), expr.getType(), expr, expr.getTextRange(), 0))
                                .nonnull();
                    else
                        return Stream.empty();
                else if (TypeConversionUtil.isAssignable(arrayType.getComponentType(), expressionType))
                    return Stream.empty();
            return type == unpackedType ? Stream.of(info) :
                    Stream.ofNullable((Privilege) HighlightUtil.checkAssignability(HandlerSupport.unwrapType(variable), expressionType, expression, variable.getTextRange().union(expression.getTextRange()), 0));
        }
        return Stream.empty();
    }
    
    private static Stream<HighlightInfo.Builder> checkVariableDefaultValueNotInitialized(final PsiVariable variable) {
        final @Nullable PsiExpression expression = defaultValue(variable);
        if (expression != null)
            return PsiTreeUtil.findChildrenOfAnyType(expression, false, PsiReferenceExpression.class).stream()
                    .filter(reference -> reference.resolve() == variable)
                    .map(reference -> HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(reference).descriptionAndTooltip(JavaErrorBundle.message("variable.not.initialized", variable.getName())));
        return Stream.empty();
    }
    
    @Hook(value = HighlightClassUtil.class, isStatic = true, at = @At(method = @At.MethodInsn(name = "getConstructors")), before = false, capture = true)
    private static PsiMethod[] qualifiedNewCalledInConstructors(final PsiMethod capture[], final PsiClass $this) = Stream.of(capture).filter(it -> !(it instanceof DerivedMethod)).toArray(PsiMethod[]::new);
    
}
