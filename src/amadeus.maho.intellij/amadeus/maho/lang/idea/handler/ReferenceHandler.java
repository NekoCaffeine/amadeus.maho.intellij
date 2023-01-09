package amadeus.maho.lang.idea.handler;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.impl.source.tree.java.PsiLocalVariableImpl;
import com.intellij.psi.util.CachedValuesManager;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.ExtensibleMembers;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.handler.base.HandlerMarker;
import amadeus.maho.lang.idea.light.LightExpression;
import amadeus.maho.lang.idea.light.LightMethod;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.reference.Mutable;
import amadeus.maho.lang.reference.Observable;
import amadeus.maho.lang.reference.Overwritable;
import amadeus.maho.lang.reference.Puppet;
import amadeus.maho.lang.reference.Readable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.reference.Reference;
import amadeus.maho.util.runtime.StringHelper;
import amadeus.maho.util.tuple.Tuple2;

import static amadeus.maho.lang.idea.handler.GetterHandler.REFERENCE_GETTER;

@TransformProvider
public abstract class ReferenceHandler<A extends Annotation> extends BaseHandler<A> {
    
    public static final int PRIORITY = FieldDefaultsHandler.PRIORITY >> 2;
    
    @Handler(value = Readable.class, ranges = Handler.Range.FIELD, priority = PRIORITY)
    public static class ReadableHandler extends ReferenceHandler<Readable> { }
    
    @Handler(value = Mutable.class, ranges = Handler.Range.FIELD, priority = PRIORITY)
    public static class MutableHandler extends ReferenceHandler<Mutable> { }
    
    @Handler(value = Observable.class, ranges = Handler.Range.FIELD, priority = PRIORITY)
    public static class ObservableHandler extends ReferenceHandler<Observable> { }
    
    @Handler(value = Overwritable.class, ranges = Handler.Range.FIELD, priority = PRIORITY)
    public static class OverwritableHandler extends ReferenceHandler<Overwritable> { }
    
    @Handler(value = Puppet.class, ranges = Handler.Range.FIELD, priority = PRIORITY)
    public static class PuppetHandler extends ReferenceHandler<Puppet> { }
    
    public static final List<Class<? extends Annotation>> references = Stream.of(ReferenceHandler.class.getDeclaredClasses())
            .map(clazz -> clazz.getAnnotation(Handler.class))
            .nonnull()
            .map(Handler::value)
            .collect(Collectors.toList());
    
    @Override
    public void check(final PsiElement tree, final A annotation, final PsiAnnotation annotationTree, final ProblemsHolder holder, final QuickFixFactory quickFix) {
        if (tree instanceof PsiModifierListOwner owner && findReferences(owner) > 1)
            holder.registerProblem(annotationTree, "There can only be one reference mark.", ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
        
    }
    
    @Override
    public void transformModifiers(final PsiElement tree, final A annotation, final PsiAnnotation annotationTree, final HashSet<String> result) {
        if (tree instanceof PsiField)
            result += PsiModifier.FINAL;
    }
    
    @Override
    public void wrapperType(final PsiTypeElement tree, final A annotation, final PsiAnnotation annotationTree, final PsiType result[]) {
        if (!(tree.getParent() instanceof final PsiMethod method && method.getParent() instanceof PsiClass owner && owner.isInterface() && !method.hasModifierProperty(PsiModifier.STATIC)
              && method.getReturnTypeElement() == tree && method.getParameterList().getParametersCount() == 0))
            doWrapperType(tree, annotation, annotationTree, result);
    }
    
    protected void doWrapperType(final PsiTypeElement tree, final A annotation, final PsiAnnotation annotationTree, final PsiType result[]) {
        final String type = Reference.class.getPackageName() + "." + annotation.annotationType().getSimpleName();
        if (result[0] instanceof PsiPrimitiveType) {
            result[0] = PsiType.getTypeByName(type + "." + StringHelper.upper(result[0].getCanonicalText(), 0), tree.getProject(), tree.getResolveScope());
        } else {
            final PsiClass refClass = JavaPsiFacade.getInstance(tree.getProject()).findClass(type, tree.getResolveScope());
            if (refClass != null)
                result[0] = PsiElementFactory.getInstance(tree.getProject()).createType(refClass, result[0]);
            else
                result[0] = PsiType.getTypeByName(type, tree.getProject(), tree.getResolveScope());
        }
    }
    
    @Override
    public void processMethod(final PsiMethod tree, final A annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) {
        final List<Tuple2<Getter, PsiAnnotation>> annotations = HandlerMarker.EntryPoint.getAnnotationsByTypeWithOuter(tree, Getter.class);
        final @Nullable PsiTypeElement typeElement = tree.getReturnTypeElement();
        final @Nullable PsiType returnType = tree.getReturnType();
        if (typeElement != null && returnType != null && !annotations.isEmpty() && context.isInterface() && !tree.hasModifierProperty(PsiModifier.STATIC) && tree.getParameterList().getParametersCount() == 0) {
            final Tuple2<Getter, PsiAnnotation> getter = annotations.get(0);
            final PsiType p_type[] = { returnType };
            doWrapperType(typeElement, annotation, annotationTree, p_type);
            final LightMethod refMethodTree = { tree, tree.getName() + REFERENCE_GETTER, tree, annotationTree };
            refMethodTree.setMethodReturnType(p_type[0]);
            if (members.shouldInject(refMethodTree)) {
                refMethodTree.setNavigationElement(tree);
                refMethodTree.setContainingClass(context);
                refMethodTree.addModifiers(PsiModifier.PUBLIC);
                refMethodTree.addModifiers(PsiModifier.ABSTRACT);
                followAnnotationWithoutNullable(tree.getModifierList(), refMethodTree.getModifierList());
                followAnnotation(getter.v2, GetterHandler.ON_REFERENCE_GETTER, refMethodTree.getModifierList());
                refMethodTree.setMethodKind(handler().value().getCanonicalName());
                members.inject(refMethodTree);
            }
        }
    }
    
    @Override
    public void collectRelatedTarget(final PsiModifierListOwner tree, final A annotation, final PsiAnnotation annotationTree, final Set<PsiNameIdentifierOwner> targets) {
        if (tree instanceof final PsiMethod method) {
            final @Nullable PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
                final List<Tuple2<Getter, PsiAnnotation>> annotations = HandlerMarker.EntryPoint.getAnnotationsByTypeWithOuter(method, Getter.class);
                final @Nullable PsiTypeElement typeElement = method.getReturnTypeElement();
                final @Nullable PsiType returnType = method.getReturnType();
                if (typeElement != null && returnType != null && !annotations.isEmpty() && containingClass.isInterface() && !method.hasModifierProperty(PsiModifier.STATIC) && method.getParameterList().getParametersCount() == 0) {
                    final PsiType p_type[] = { returnType };
                    doWrapperType(typeElement, annotation, annotationTree, p_type);
                    final LightMethod refMethodTree = { method, method.getName() + REFERENCE_GETTER, method, annotationTree };
                    refMethodTree.setMethodReturnType(p_type[0]);
                    targets += containingClass.findMethodBySignature(refMethodTree, false);
                }
            }
        }
    }
    
    @Hook(capture = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static @Nullable PsiExpression getInitializer(final @Nullable PsiExpression capture, final PsiFieldImpl $this) = findReferences($this) > 0 ? capture ?? lightExpression($this) : capture;
    
    @Hook(capture = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static @Nullable PsiExpression getInitializer(final @Nullable PsiExpression capture, final PsiLocalVariableImpl $this) = findReferences($this) > 0 ? capture ?? lightExpression($this) : capture;
    
    private static LightExpression lightExpression(final PsiVariable $this)
            = CachedValuesManager.getProjectPsiDependentCache($this, _ -> new LightExpression($this.getManager(), JavaLanguage.INSTANCE, $this, HandlerMarker.EntryPoint.unwrapType($this)));
    
    public static long findReferences(final PsiModifierListOwner tree) = references.stream()
            .map(annotationType -> HandlerMarker.EntryPoint.getAnnotationsByTypeWithOuter(tree, annotationType))
            .mapToInt(List::size)
            .sum();
    
}
