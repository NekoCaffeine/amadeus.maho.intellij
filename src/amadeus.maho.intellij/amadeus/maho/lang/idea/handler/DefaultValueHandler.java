package amadeus.maho.lang.idea.handler;

import java.lang.invoke.VarHandle;
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
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.DataFlowInspectionBase;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.dataFlow.StandardDataFlowRunner;
import com.intellij.codeInspection.dataFlow.java.JavaDfaValueFactory;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderUtil;
import com.intellij.lang.java.parser.BasicDeclarationParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameIdentifierOwner;
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
import com.intellij.psi.impl.source.PsiParameterImpl;
import com.intellij.psi.impl.source.PsiRecordComponentImpl;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;

import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.idea.handler.base.BaseSyntaxHandler;
import amadeus.maho.lang.idea.handler.base.ExtensibleMembers;
import amadeus.maho.lang.idea.handler.base.HandlerSupport;
import amadeus.maho.lang.idea.handler.base.Syntax;
import amadeus.maho.lang.idea.light.LightElement;
import amadeus.maho.lang.idea.light.LightMethod;
import amadeus.maho.lang.idea.light.LightModifierList;
import amadeus.maho.lang.idea.light.LightParameter;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;

import static amadeus.maho.lang.idea.IDEAContext.*;
import static amadeus.maho.lang.idea.handler.DefaultValueHandler.PRIORITY;
import static amadeus.maho.util.bytecode.Bytecodes.*;
import static com.intellij.psi.JavaTokenType.EQ;

@TransformProvider
@Syntax(priority = PRIORITY)
public class DefaultValueHandler extends BaseSyntaxHandler {
    
    public static final int PRIORITY = ConstructorHandler.PRIORITY << 1;
    
    @NoArgsConstructor
    public static class DerivedMethod extends LightMethod { }
    
    @ToString
    @EqualsAndHashCode
    public record DefaultParameter(PsiParameter parameter, @Nullable PsiExpression defaultValue) { }
    
    public static List<DerivedMethod> derivedMethods(final PsiMethod method) = CachedValuesManager.getProjectPsiDependentCache(method, it -> {
        if (!(it instanceof DerivedMethod)) {
            final @Nullable PsiType returnType = it.getReturnType();
            final LinkedList<DefaultParameter> defaultParameters = { };
            Stream.of(it.getParameterList().getParameters())
                    .map(parameter -> new DefaultParameter(parameter, (recordComponentForParameter(parameter) ?? parameter).getInitializer()))
                    .filter(defaultParameter -> defaultParameter.defaultValue() != null)
                    .forEach(defaultParameters::addFirst);
            if (!defaultParameters.isEmpty()) {
                final @Nullable PsiClass context = it.getContainingClass();
                if (context != null)
                    return Stream.concat(Stream.of(new ArrayList<>(defaultParameters)), Stream.generate(() -> defaultParameters)
                                    .limit(defaultParameters.size() - 1)
                                    .peek(LinkedList::removeLast)
                                    .map(ArrayList::new))
                            .map(list -> {
                                final List<PsiParameter> parameters = new ArrayList<>(List.of(it.getParameterList().getParameters()));
                                parameters.removeIf(parameter -> list.stream().anyMatch(defaultParameter -> defaultParameter.parameter() == parameter || defaultParameter.parameter().equals(parameter)));
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
                                followAnnotation(it.getModifierList(), modifierList);
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
    
    @Hook
    private static Hook.Result visitParameter(final JavaSpacePropertyProcessor $this, final PsiParameter parameter) {
        if ((Privilege) $this.myType1 == EQ || (Privilege) $this.myType2 == EQ) {
            (Privilege) $this.createSpaceInCode(((Privilege) $this.mySettings).SPACE_AROUND_ASSIGNMENT_OPERATORS);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result visitRecordComponent(final JavaSpacePropertyProcessor $this, final PsiRecordComponent recordComponent) {
        if ((Privilege) $this.myType1 == EQ || (Privilege) $this.myType2 == EQ) {
            (Privilege) $this.createSpaceInCode(((Privilege) $this.mySettings).SPACE_AROUND_ASSIGNMENT_OPERATORS);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook(at = @At(method = @At.MethodInsn(name = "done"), ordinal = 1))
    private static void parseListElement(final BasicDeclarationParser $this, final PsiBuilder builder, final boolean typed, final int typeFlags, final IElementType type) {
        if (PsiBuilderUtil.expect(builder, EQ)) {
            final PsiBuilder.Marker marker = ((Privilege) $this.myParser).getExpressionParser().parse(builder);
            if (marker == null)
                JavaParserUtil.error(builder, JavaErrorBundle.message("expected.expression"));
        }
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
        final @Nullable PsiParameter context = PsiTreeUtil.getContextOfType(place, PsiParameter.class);
        if (context != null) {
            final PsiParameter parameters[] = element.getParameterList().getParameters();
            final int index = ArrayHelper.indexOf(parameters, context), p_index[] = { -1 };
            return Stream.of(parameters).noneMatch(parameter -> (++p_index[0] <= index || parameter.getInitializer() == null) && !processor.execute(parameter, state));
        }
        return true;
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
                                .anyMatch(component -> component.getInitializer() != null))
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
    
    @SneakyThrows
    @Getter(lazy = true)
    private static VarHandle holder = MethodHandleHelper.lookup().unreflectVarHandle(Stream.of(Class.forName("com.intellij.codeInspection.dataFlow.DataFlowInspectionBase$1").getDeclaredFields())
            .filter(field -> field.getType() == ProblemsHolder.class)
            .findFirst()
            .map(field -> { field.setAccessible(true); return field; })
            .orElseThrow(NoSuchFieldException::new));
    
    @Hook(target = "com.intellij.codeInspection.dataFlow.DataFlowInspectionBase$1")
    private static void visitMethod(final JavaElementVisitor $this, final PsiMethod method) {
        final List<PsiParameter> parameters = Stream.of(method.getParameterList().getParameters()).filter(PsiVariable::hasInitializer).toList();
        if (!parameters.isEmpty()) {
            final StandardDataFlowRunner runner = { method.getProject() };
            final DataFlowInspectionBase inspection = Privilege.Outer.access($this);
            final ProblemsHolder holder = (ProblemsHolder) holder().get($this);
            parameters.forEach(parameter -> (Privilege) inspection.analyzeDfaWithNestedClosures(parameter.getInitializer(), holder, runner, List.of((Privilege) runner.createMemoryState())));
        }
    }
    
    @Hook(value = NullabilityProblemKind.class, isStatic = true, at = @At(var = @At.VarInsn(opcode = ISTORE, var = 8)), capture = true)
    private static boolean getAssignmentProblem(final boolean capture, final PsiAssignmentExpression assignment, final PsiExpression expression, final PsiExpression context) = true;
    
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
    
    @Hook(forceReturn = true)
    private static boolean hasInitializer(final PsiParameterImpl $this) = $this.getLastChild() instanceof PsiExpression;
    
    @Hook(forceReturn = true)
    private static @Nullable PsiExpression getInitializer(final PsiParameterImpl $this) = $this.getLastChild() instanceof PsiExpression expression ? expression : null;
    
    @Hook(forceReturn = true)
    private static boolean hasInitializer(final PsiRecordComponentImpl $this) = $this.getLastChild() instanceof PsiExpression;
    
    @Hook(forceReturn = true)
    private static @Nullable PsiExpression getInitializer(final PsiRecordComponentImpl $this) = $this.getLastChild() instanceof PsiExpression expression ? expression : null;
    
    @Hook(value = JavaDfaValueFactory.class, isStatic = true)
    private static Hook.Result ignoreInitializer(final PsiVariable variable) = Hook.Result.falseToVoid(variable instanceof PsiParameter || variable instanceof PsiRecordComponent);
    
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
        return ~Stream.of(containingClass.getRecordComponents()).filter(component -> name.equals(component.getName()));
    }
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkVariableInitializerType(final PsiVariable variable) = Hook.Result.falseToVoid(variable instanceof PsiParameter || variable instanceof PsiRecordComponent, null);
    
    private static Stream<HighlightInfo.Builder> checkVariableDefaultValueNotInitialized(final PsiVariable variable) {
        final @Nullable PsiExpression expression = variable.getInitializer();
        if (expression != null)
            return PsiTreeUtil.findChildrenOfAnyType(expression, false, PsiReferenceExpression.class).stream()
                    .filter(reference -> reference.resolve() == variable)
                    .map(reference -> HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(reference).descriptionAndTooltip(JavaErrorBundle.message("variable.not.initialized", variable.getName())));
        return Stream.empty();
    }
    
    private static Stream<HighlightInfo.Builder> checkVariableDefaultValueType(final PsiVariable variable) {
        final @Nullable PsiExpression expression = variable.getInitializer();
        if (expression == null)
            return Stream.empty();
        final PsiType variableType = variable.getType();
        if (variableType instanceof PsiEllipsisType ellipsisType) {
            final @Nullable HighlightInfo.Builder builder = (Privilege) HighlightUtil.checkAssignability(ellipsisType.getComponentType(), expression.getType(), expression, expression.getTextRange(), 0);
            if (builder == null)
                return Stream.empty();
        }
        return Stream.ofNullable((Privilege) HighlightUtil.checkAssignability(HandlerSupport.unwrapType(variable), expression.getType(), expression, expression.getTextRange(), 0));
    }
    
    @Hook(value = HighlightClassUtil.class, isStatic = true, at = @At(method = @At.MethodInsn(name = "getConstructors")), before = false, capture = true)
    private static PsiMethod[] qualifiedNewCalledInConstructors(final PsiMethod capture[], final PsiClass $this) = Stream.of(capture).filter(it -> !(it instanceof DerivedMethod)).toArray(PsiMethod[]::new);
    
}
