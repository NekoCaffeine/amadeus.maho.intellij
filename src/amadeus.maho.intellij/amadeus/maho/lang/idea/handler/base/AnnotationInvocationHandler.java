package amadeus.maho.lang.idea.handler.base;

import java.lang.annotation.Annotation;
import java.lang.annotation.IncompleteAnnotationException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Stream;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.annotation.AnnotationHandler;
import amadeus.maho.util.annotation.AnnotationType;
import amadeus.maho.util.dynamic.ClassLocal;
import amadeus.maho.util.runtime.ObjectHelper;
import amadeus.maho.util.runtime.TypeHelper;
import amadeus.maho.util.tuple.Tuple2;

@TransformProvider
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class AnnotationInvocationHandler implements InvocationHandler {
    
    @Extension
    public interface Ext {
        
        static <A extends Annotation> @Nullable PsiClassType accessPsiClass(final A annotation, final Function<A, Class<?>> accessor) = ~tryAccessPsiClasses(annotation, accessor);
        
        static <A extends Annotation> Stream<? extends PsiClassType> accessPsiClasses(final A annotation, final Function<A, Class<?>[]> accessor) = tryAccessPsiClasses(annotation, accessor);
        
        private static <A extends Annotation> Stream<? extends PsiClassType> tryAccessPsiClasses(final A annotation, final Function<A, ?> accessor) {
            try {
                accessor.apply(annotation);
                return Stream.empty();
            } catch (final PsiClassesException e) {
                return e.classes().stream();
            }
        }
        
    }
    
    public static class AuxEvaluator implements PsiConstantEvaluationHelper.AuxEvaluator {
        
        ConcurrentHashMap<PsiElement, Object> cache = { };
        
        @Override
        public @Nullable Object computeExpression(final PsiExpression expression, final PsiConstantEvaluationHelper.AuxEvaluator auxEvaluator) = null;
        
        @Override
        public ConcurrentMap<PsiElement, Object> getCacheMap(final boolean overflow) = cache;
        
    }
    
    @NoArgsConstructor
    public static class IncompletePsiAnnotationException extends IncompleteAnnotationException {
        
        @Override
        public synchronized Throwable fillInStackTrace() = this;
        
    }
    
    Class<? extends Annotation> annotationClass;
    
    AnnotationType annotationType = AnnotationType.instance(annotationClass);
    
    @Default
    PsiAnnotation annotationTree;
    
    @Nullable
    PsiAnnotation annotationTreeCopy = annotationTree == null ? null : JavaPsiFacade.getElementFactory(annotationTree.getProject()).createAnnotationFromText(annotationTree.getText(), annotationTree);
    
    ConcurrentHashMap<String, Object> evaluateCache = { };
    
    @Override
    public Object invoke(final Object proxy, final Method method, final Object... args) {
        final String name = method.getName();
        return switch (name) {
            case "hashCode"       -> ObjectHelper.hashCode(annotationClass, annotationTree);
            case "equals"         -> args[0] != null && args[0].getClass() == AnnotationInvocationHandler.class &&
                                     ObjectHelper.equals(annotationClass, ((AnnotationInvocationHandler) args[0]).annotationClass) &&
                                     ObjectHelper.equals(annotationTree, ((AnnotationInvocationHandler) args[0]).annotationTree);
            case "toString"       -> toStringImpl();
            case "annotationType" -> annotationClass;
            default               -> evaluateCache.computeIfAbsent(name, key -> {
                final Tuple2<Object, String> tuple = attributeValueOrError(key);
                final @Nullable Object value = tuple.v1;
                if (value instanceof PsiClassesException accessEx)
                    throw accessEx;
                if (value == null)
                    throw new IncompletePsiAnnotationException(annotationClass, STR."key: '\{key}' (Unable to find attribute in '\{annotationTree?.getText() ?? STR."@\{annotationClass.getName()}"}': \{tuple.v2})");
                return value;
            });
        };
    }
    
    public Tuple2<Object, String> attributeValueOrError(final String attributeName) {
        if (annotationTreeCopy != null) {
            final @Nullable PsiNameValuePair attribute = AnnotationUtil.findDeclaredAttribute(annotationTreeCopy, attributeName);
            final @Nullable PsiAnnotationMemberValue value = attribute == null ? null : attribute.getValue();
            if (value != null) {
                final Method method = annotationType.members()[attributeName];
                if (Annotation.class.isAssignableFrom(method.getReturnType()) && value instanceof PsiAnnotation valueAnnotation) {
                    final List<? extends Tuple2<? extends Annotation, PsiAnnotation>> result
                            = HandlerSupport.getAnnotationsByType(annotationTreeCopy.getProject(), (Class<? extends Annotation>) method.getReturnType(), valueAnnotation);
                    if (!result.isEmpty())
                        return { result[0].v1, null };
                }
                if (Annotation[].class.isAssignableFrom(method.getReturnType())) {
                    final List<? extends Tuple2<? extends Annotation, PsiAnnotation>> result
                            = HandlerSupport.getAnnotationsByType(annotationTreeCopy.getProject(), (Class<? extends Annotation>) method.getReturnType().getComponentType(),
                            value instanceof PsiAnnotation valueAnnotation ? new PsiAnnotation[]{ valueAnnotation } : value instanceof PsiArrayInitializerMemberValue array ?
                                    Stream.of(array.getInitializers()).cast(PsiAnnotation.class).toArray(PsiAnnotation.ARRAY_FACTORY::create) : PsiAnnotation.EMPTY_ARRAY);
                    return { result.stream().map(Tuple2::v1).toArray(TypeHelper.arrayConstructor(method.getReturnType().getComponentType())), null };
                }
                // If you use the JavaConstantExpressionEvaluator directly you will not be able to evaluate it due to incorrect caching, which only happens when re-parsing.
                @Nullable Object result = computeConstant(value, method.getReturnType());
                if (result instanceof PsiClassesException)
                    return { result, null };
                if (result != null && result.getClass() != method.getReturnType() && result.getClass() == method.getReturnType().getComponentType())
                    result = Stream.of(result).toArray(TypeHelper.arrayConstructor(result.getClass()));
                if (result == null)
                    return { null, STR."Unable to evaluate annotation value '\{value.getText()}'" };
                else
                    return { result, null };
            }
        }
        try {
            final @Nullable Object defaultValue = annotationType.memberDefaults().get(attributeName);
            if (defaultValue == null)
                return { null, STR."No default value is specified for method \{attributeName}" };
            else
                return { defaultValue, null };
        } catch (final NoSuchMethodException e) { return { null, STR."Method not found: \{attributeName}" }; }
    }
    
    protected @Nullable Object computeConstant(final PsiAnnotationMemberValue value, final Class<?> type) {
        if (type == Class.class || type == Class[].class) {
            if (value instanceof PsiArrayInitializerMemberValue array)
                return new PsiClassesException(Stream.of(array.getInitializers()).map(this::tryResolvePsiClass).nonnull().toList());
            final @Nullable PsiClassType classType = tryResolvePsiClass(value);
            return classType != null ? new PsiClassesException(List.of(classType)) : null;
        }
        if (type.isEnum()) {
            if (value instanceof PsiReferenceExpression expression) {
                final @Nullable String name = expression.getReferenceName();
                if (name != null)
                    return ((Privilege) type.enumConstantDirectory())[name];
            }
            return null; // must be enum const
        }
        return switch (value) {
            case PsiLiteral literal                   -> literal.getValue();
            case PsiArrayInitializerMemberValue array -> Stream.of(array.getInitializers()).map(initializer -> computeConstant(initializer, type.getComponentType())).toArray(TypeHelper.arrayConstructor(type.getComponentType()));
            case PsiExpression expression             -> JavaConstantExpressionEvaluator.computeConstantExpression(expression, new HashSet<>(), false, new AuxEvaluator());
            default                                   -> null;
        };
    }
    
    protected @Nullable PsiClassType tryResolvePsiClass(final PsiAnnotationMemberValue value)
            = value instanceof PsiClassObjectAccessExpression expression ? expression.getOperand().getType() instanceof PsiClassType classType ? classType : null : null;
    
    private String toStringImpl() {
        final StringBuilder result = { 1 << 6 };
        result.append('@').append(annotationClass.getName()).append('(');
        if (annotationTree != null) {
            boolean firstMember = true;
            for (final PsiNameValuePair pair : annotationTree.getParameterList().getAttributes()) {
                if (firstMember)
                    firstMember = false;
                else
                    result.append(", ");
                final @Nullable String name = pair.getName();
                result.append(name == null ? "value" : name).append('=');
                final @Nullable PsiAnnotationMemberValue value = pair.getValue();
                result.append(value == null ? "null" : value.getText());
            }
        }
        return result.append(')').toString();
    }
    
    private static final List<Class<?>> skipReturnTypes = List.of(Annotation.class, Class.class, Annotation[].class, Class[].class);
    
    private static final ClassLocal<List<Method>> annotationMethodLocal = {
            clazz -> Stream.of(clazz.getDeclaredMethods())
                    .filter(method -> method.getParameterCount() == 0 && !Modifier.isStatic(method.getModifiers()))
                    .filter(method -> !skipReturnTypes.contains(method.getReturnType()))
                    .toList()
    };
    
    public static @Nullable AnnotationInvocationHandler asOneOfUs(final Annotation annotation)
            = Proxy.isProxyClass(annotation.getClass()) && Proxy.getInvocationHandler(annotation) instanceof AnnotationInvocationHandler handler ? handler : null;
    
    public static <A extends Annotation> @Nullable A make(final Class<A> annotationType, final @Nullable PsiAnnotation annotation = null) {
        if (annotation == null)
            return AnnotationHandler.defaultInstance(annotationType);
        final AnnotationInvocationHandler handler = { annotationType, annotation };
        final A instance = (A) Proxy.newProxyInstance(annotationType.getClassLoader(), new Class<?>[]{ annotationType }, handler);
        for (final Method method : annotationMethodLocal[annotationType])
            if (!testMethod(instance, method))
                return null;
        return instance;
    }
    
    private static <A extends Annotation> boolean testMethod(final A instance, final Method method) {
        try {
            method.invoke(instance);
            return true;
        } catch (final Throwable throwable) {
            Throwable copy = throwable;
            do
                if (copy instanceof ProcessCanceledException canceled)
                    throw canceled;
            while ((copy = copy.getCause()) != null);
            return false;
        }
    }
    
}
