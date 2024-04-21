package amadeus.maho.lang.idea.handler;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.stream.Stream;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;

import amadeus.maho.lang.BinaryMapping;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.ExtensibleMembers;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.light.LightField;
import amadeus.maho.lang.idea.light.LightMethod;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.LookupHelper;
import amadeus.maho.util.serialization.BinaryMapper;

import static amadeus.maho.lang.idea.IDEAContext.followAnnotation;
import static com.intellij.psi.PsiModifier.*;

public abstract class PlaceholderHandler<A extends Annotation> extends BaseHandler<A> {
    
    public static final int PRIORITY = ConstructorHandler.PRIORITY;
    
    @Handler(value = ToString.class, priority = PRIORITY)
    public static class ToStringHandler extends PlaceholderHandler<ToString> {
        
        protected static final Method toString = LookupHelper.method1(Object::toString);
        
        @Override
        protected Stream<Method> methods(final ToString annotation) = Stream.of(toString);
        
    }
    
    @Handler(value = EqualsAndHashCode.class, priority = PRIORITY)
    public static class EqualsAndHashCodeHandler extends PlaceholderHandler<EqualsAndHashCode> {
        
        protected static final Method
                equals   = LookupHelper.method2(Object::equals),
                hashCode = LookupHelper.method1(Object::hashCode);
        
        @Override
        protected Stream<Method> methods(final EqualsAndHashCode annotation) = Stream.of(equals, hashCode);
        
    }
    
    @TransformProvider
    @Handler(value = BinaryMapping.class, priority = PRIORITY)
    public static class BinaryMappingHandler extends PlaceholderHandler<BinaryMapping> {
        
        @SneakyThrows
        protected static final Method
                serialization   = LookupHelper.methodV2(BinaryMapper::serialization),
                deserialization = LookupHelper.methodV2(BinaryMapper::deserialization),
                write           = LookupHelper.methodV2(BinaryMapper::write),
                read            = LookupHelper.methodV2(BinaryMapper::read);
        
        @Override
        protected Stream<Method> methods(final BinaryMapping annotation) = annotation.metadata() ? Stream.of(deserialization, read) : Stream.of(serialization, deserialization, write, read);
        
        @Override
        protected Stream<Class<?>> interfaces(final BinaryMapping annotation) = annotation.eofMark() ?
                Stream.of(BinaryMapper.class, BinaryMapper.EOFMark.class) : Stream.of(BinaryMapper.class);
        
        @Override
        public void processClass(final PsiClass tree, final BinaryMapping annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) {
            if (tree != context)
                return;
            super.processClass(tree, annotation, annotationTree, members, context);
            if (annotation.eofMark()) {
                final LightField mark = { tree, "eofMark", PsiTypes.booleanType(), annotationTree };
                if (members.shouldInject(mark)) {
                    mark.getModifierList().addAnnotation(Getter.class.getCanonicalName());
                    mark.setNavigationElement(tree);
                    mark.setContainingClass(context);
                    mark.addModifiers(PUBLIC, TRANSIENT);
                    members.inject(mark);
                }
            }
        }
        
        @Override
        public void check(final PsiElement tree, final BinaryMapping annotation, final PsiAnnotation annotationTree, final ProblemsHolder holder, final QuickFixFactory quickFix) {
            super.check(tree, annotation, annotationTree, holder, quickFix);
            if (tree instanceof PsiClass psiClass)
                Stream.of(psiClass.getFields())
                        .filter(field -> field.hasAnnotation(BinaryMapping.Constant.class.getCanonicalName()) && field.hasAnnotation(BinaryMapping.ForWrite.class.getCanonicalName()))
                        .forEach(field -> holder.registerProblem(field, "@Constant is incompatible with @ForWrite", ProblemHighlightType.GENERIC_ERROR,
                                quickFix.createDeleteFix(field.getAnnotation(BinaryMapping.Constant.class.getCanonicalName()))));
        }
        
        @Hook(value = HighlightUtil.class, isStatic = true)
        private static Hook.Result checkIllegalForwardReferenceToField(final PsiReferenceExpression expression, final PsiField referencedField)
                = Hook.Result.falseToVoid(PsiTreeUtil.getParentOfType(expression, PsiVariable.class, PsiStatement.class) instanceof PsiVariable variable && variable.hasAnnotation(BinaryMapping.ForWrite.class.getCanonicalName()), null);
        
    }
    
    protected abstract Stream<Method> methods(final A annotation);
    
    protected Stream<Class<?>> interfaces(final A annotation) = Stream.empty();
    
    protected Stream<PsiClass> interfaceTypes(final JavaPsiFacade facade, final GlobalSearchScope scope, final A annotation) = interfaces(annotation).map(itf -> facade.findClass(itf.getCanonicalName(), scope)).nonnull();
    
    @Override
    public void transformInterfaces(final PsiClass tree, final A annotation, final PsiAnnotation annotationTree, final HashSet<PsiClass> result)
            = interfaceTypes(JavaPsiFacade.getInstance(tree.getProject()), tree.getResolveScope(), annotation).forEach(result::add);
    
    @Override
    public void transformInterfaceTypes(final PsiClass tree, final A annotation, final PsiAnnotation annotationTree, final HashSet<PsiClassType> result)
            = interfaceTypes(JavaPsiFacade.getInstance(tree.getProject()), tree.getResolveScope(), annotation).map(PsiElementFactory.getInstance(tree.getProject())::createType).forEach(result::add);
    
    @Override
    public void processClass(final PsiClass tree, final A annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) {
        if (tree != context)
            return;
        methods(annotation).forEach(method -> {
            final LightMethod methodTree = { tree, method.getName(), annotationTree };
            Stream.of(method.getParameters()).forEach(parameter -> methodTree.addParameter(parameter.getName(), parameter.getType().getCanonicalName(), parameter.isVarArgs()));
            methodTree.setMethodReturnType(JavaPsiFacade.getElementFactory(tree.getProject()).createTypeFromText(method.getReturnType().getCanonicalName(), tree));
            if (members.shouldInject(methodTree)) {
                methodTree.setNavigationElement(annotationTree);
                methodTree.setContainingClass(context);
                methodTree.addModifiers(Modifier.toString(method.getModifiers() & ~(Modifier.ABSTRACT | Modifier.INTERFACE)).split(" "));
                Stream.of(method.getExceptionTypes()).forEach(exType -> methodTree.getThrowsList().addReference(exType.getCanonicalName()));
                followAnnotation(annotationTree, "on", methodTree.getModifierList());
                methodTree.setMethodKind(handler().value().getCanonicalName());
                members.inject(methodTree);
            }
        });
    }
    
    @Override
    public void check(final PsiElement tree, final A annotation, final PsiAnnotation annotationTree, final ProblemsHolder holder, final QuickFixFactory quickFix) {
        if (tree instanceof PsiClass psiClass && psiClass.isInterface())
            holder.registerProblem(annotationTree, JavaErrorBundle.message("not.allowed.in.interface"), ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
    }
    
}
