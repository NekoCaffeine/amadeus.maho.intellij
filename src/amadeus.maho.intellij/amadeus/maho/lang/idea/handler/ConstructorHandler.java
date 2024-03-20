package amadeus.maho.lang.idea.handler;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.MoveInitializerToConstructorAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.java.JavaDfaValueFactory;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.ExtensibleMembers;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.handler.base.HandlerSupport;
import amadeus.maho.lang.idea.handler.base.ImplicitUsageChecker;
import amadeus.maho.lang.idea.light.LightDefaultParameter;
import amadeus.maho.lang.idea.light.LightElement;
import amadeus.maho.lang.idea.light.LightMethod;
import amadeus.maho.lang.idea.light.LightParameter;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

import static amadeus.maho.lang.idea.IDEAContext.*;

@TransformProvider
public abstract class ConstructorHandler<A extends Annotation> extends BaseHandler<A> {
    
    public static final int PRIORITY = SetterHandler.PRIORITY << 2;
    
    @Handler(value = NoArgsConstructor.class, priority = PRIORITY + 1)
    public static class NoArgsConstructorHandler extends ConstructorHandler<NoArgsConstructor> {
        
        @Override
        protected AccessLevel accessLevel(final NoArgsConstructor annotation) = annotation.value();
        
        @Override
        protected boolean varargs(final NoArgsConstructor annotation) = annotation.varargs();
        
        @Override
        protected Stream<PsiField> fields(final List<PsiField> fields) = Stream.empty();
        
    }
    
    @Handler(value = AllArgsConstructor.class, priority = PRIORITY - 1)
    public static class AllArgsConstructorHandler extends ConstructorHandler<AllArgsConstructor> {
        
        @Override
        protected AccessLevel accessLevel(final AllArgsConstructor annotation) = annotation.value();
        
        @Override
        protected boolean varargs(final AllArgsConstructor annotation) = annotation.varargs();
        
        @Override
        protected Stream<PsiField> fields(final List<PsiField> fields) = fields.stream()
                .filter(field -> !field.hasModifierProperty(PsiModifier.STATIC))
                .filter(field -> !field.hasModifierProperty(PsiModifier.FINAL) || field.getInitializer() == null || isDefaultField(field))
                .filter(field -> !(HandlerSupport.lookupAnnotation(field, Getter.class)?.lazy() ?? false))
                .filter(BaseHandler::nonGenerating);
        
    }
    
    @Handler(value = RequiredArgsConstructor.class, priority = PRIORITY)
    public static class RequiredArgsConstructorHandler extends ConstructorHandler<RequiredArgsConstructor> {
        
        @Override
        protected AccessLevel accessLevel(final RequiredArgsConstructor annotation) = annotation.value();
        
        @Override
        protected boolean varargs(final RequiredArgsConstructor annotation) = annotation.varargs();
        
        @Override
        protected Stream<PsiField> fields(final List<PsiField> fields) = fields.stream()
                .filter(field -> !field.hasModifierProperty(PsiModifier.STATIC))
                .filter(field -> field.hasModifierProperty(PsiModifier.FINAL) && field.getInitializer() == null || isDefaultField(field))
                .filter(field -> !(HandlerSupport.lookupAnnotation(field, Getter.class)?.lazy() ?? false))
                .filter(BaseHandler::nonGenerating);
        
    }
    
    public static boolean isDefaultField(final PsiField field) = field.hasAnnotation(Default.class.getCanonicalName());
    
    public static final ArrayList<ConstructorHandler<?>> constructorHandlers = { ConstructorHandler.class.getDeclaredClasses().length };
    
    { constructorHandlers += this; }
    
    @Hook(value = JavaDfaValueFactory.class, isStatic = true)
    private static Hook.Result ignoreInitializer(final PsiVariable variable) = Hook.Result.falseToVoid(variable.hasAnnotation(Default.class.getCanonicalName()));
    
    @Hook(value = HighlightControlFlowUtil.class, isStatic = true)
    private static Hook.Result checkVariableInitializedBeforeUsage(final PsiReferenceExpression expression, final PsiVariable variable,
            final Map<PsiElement, Collection<PsiReferenceExpression>> uninitializedVarProblems, final PsiFile containingFile, final boolean ignoreFinality)
            = Hook.Result.falseToVoid(variable instanceof PsiField field && variable.getInitializer() == null && field.getContainingClass() instanceof PsiExtensibleClass extensibleClass && (
            !extensibleClass.hasAnnotation(AllArgsConstructor.class.getCanonicalName()) ||
            !extensibleClass.hasAnnotation(RequiredArgsConstructor.class.getCanonicalName()) && variable.hasModifierProperty(PsiModifier.FINAL)
    ), null);
    
    @Hook(value = HighlightControlFlowUtil.class, isStatic = true)
    private static Hook.Result isFieldInitializedAfterObjectConstruction(final PsiField field) {
        if (field.hasInitializer())
            return Hook.Result.TRUE;
        final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
        final @Nullable PsiClass containingClass = field.getContainingClass();
        return containingClass != null && (
                Stream.of(containingClass.getFields())
                        .filter(it -> it != field)
                        .filter(it -> it.hasModifierProperty(PsiModifier.STATIC) == isStatic)
                        .anyMatch(it -> HighlightControlFlowUtil.variableDefinitelyAssignedIn(field, it)) ||
                Stream.of(containingClass.getInitializers())
                        .filter(it -> it.hasModifierProperty(PsiModifier.STATIC) == isStatic)
                        .anyMatch(it -> HighlightControlFlowUtil.variableDefinitelyAssignedIn(field, it.getBody())) ||
                !isStatic && containingClass.getConstructors().length > 0 && Stream.of(containingClass.getConstructors()).allMatch(it -> Stream.concat(Stream.of(it), JavaHighlightUtil.getChainedConstructors(it).stream())
                        .anyMatch(other -> other instanceof LightMethod lightMethod && lightMethod.fieldInitialized() || other.getBody() != null && HighlightControlFlowUtil.variableDefinitelyAssignedIn(field, other.getBody())))) ?
                Hook.Result.TRUE : Hook.Result.FALSE;
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true, exactMatch = false)
    private static Collection<PsiMethod> getOrCreateMethods(final Collection<PsiMethod> capture, final MoveInitializerToConstructorAction $this) = capture.stream().filterNot(LightElement.class::isInstance).toList();
    
    protected abstract AccessLevel accessLevel(A annotation);
    
    protected abstract boolean varargs(A annotation);
    
    protected Stream<PsiField> fields(final ExtensibleMembers members) = fields(members.list(ExtensibleMembers.FIELDS));
    
    protected abstract Stream<PsiField> fields(final List<PsiField> fields);
    
    @Override
    public void processClass(final PsiClass tree, final A annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) {
        if (tree != context)
            return;
        final @Nullable String name = tree.getName();
        if (name == null)
            return;
        final PsiClass superClass = tree.getSuperClass();
        if (superClass == null || superClass.getQualifiedName() == null || tree.isInterface())
            return;
        final AccessLevel accessLevel = accessLevel(annotation);
        Optional.of((superClass.getQualifiedName().equals(Enum.class.getCanonicalName()) ? Stream.<PsiMethod>empty() : Stream.of(superClass.getMethods()))
                        .filter(PsiMethod::isConstructor)
                        .filter(method -> method.getContainingClass() == superClass)
                        .collect(Collectors.toList()))
                .filter(it -> !it.isEmpty())
                .orElseGet(() -> List.of(new LightMethod(tree, "Object").addModifier(PsiModifier.PUBLIC)))
                .forEach(constructor -> {
                    if (!constructor.hasModifierProperty(PsiModifier.PROTECTED) && !constructor.hasModifierProperty(PsiModifier.PUBLIC))
                        return;
                    final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(tree.getSuperClass(), tree, PsiSubstitutor.EMPTY);
                    final LightMethod methodTree = { tree, name, tree, annotationTree };
                    methodTree.setConstructor(true);
                    final Class<? extends Annotation> annotationType = handler().value();
                    methodTree.fieldInitialized(annotationType != NoArgsConstructor.class);
                    final Function<String, String> simplify = simplify(name);
                    final boolean varargs = varargs(annotation);
                    final List<PsiParameter> parameters = List.of(constructor.getParameterList().getParameters());
                    final List<PsiField> fields = fields(members).toList();
                    final @Nullable PsiVariable lastArg = !fields.isEmpty() ? fields[-1] : !parameters.isEmpty() ? parameters[-1] : null;
                    parameters.stream()
                            .peek(parameter -> simplify.apply(parameter.getName()))
                            .map(parameter -> new LightParameter(parameter, parameter.getName(), substitutor.substitute(parameter.getType()), parameter == lastArg && (varargs || parameter.isVarArgs()))
                                    .let(result -> followAnnotation(parameter.getModifierList(), result.getModifierList())))
                            .forEach(methodTree::addParameter);
                    fields.forEach(field -> methodTree.addParameter(
                            new LightDefaultParameter(methodTree, simplify.apply(field.getName()), field.getType(), field == lastArg && varargs, isDefaultField(field) ? field.getInitializer() : null)));
                    if (members.shouldInject(methodTree)) {
                        methodTree.setNavigationElement(annotationTree);
                        methodTree.setContainingClass(context);
                        if (accessLevel != AccessLevel.PACKAGE)
                            methodTree.addModifiers(accessLevel.name().toLowerCase(Locale.ENGLISH));
                        followAnnotation(annotationTree, "on", methodTree.getModifierList());
                        methodTree.setMethodKind(annotationType.getCanonicalName());
                        members.inject(methodTree);
                    }
                });
    }
    
    @Override
    public void check(final PsiElement tree, final A annotation, final PsiAnnotation annotationTree, final ProblemsHolder holder, final QuickFixFactory quickFix) {
        if (tree instanceof PsiClass psiClass && psiClass.isInterface())
            holder.registerProblem(annotationTree, JavaErrorBundle.message("not.allowed.in.interface"), ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
    }
    
    @Override
    public boolean isImplicitWrite(final PsiElement tree, final ImplicitUsageChecker.RefData refData) {
        if (tree instanceof PsiField) {
            final @Nullable PsiClass owner = PsiTreeUtil.getContextOfType(tree, PsiClass.class);
            if (owner != null && HandlerSupport.hasAnnotation(owner, this))
                return fields(List.of(owner.getFields())).anyMatch(field -> field == tree);
        }
        return false;
    }
    
}
