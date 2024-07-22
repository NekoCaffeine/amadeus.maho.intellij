package amadeus.maho.lang.idea.handler;

import java.util.stream.Stream;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;

import amadeus.maho.lang.Builder;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.ExtensibleMembers;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.light.LightClass;
import amadeus.maho.lang.idea.light.LightField;
import amadeus.maho.lang.idea.light.LightMethod;

import static amadeus.maho.lang.idea.IDEAContext.*;
import static amadeus.maho.lang.idea.handler.BuilderHandler.PRIORITY;

@Handler(value = Builder.class, priority = PRIORITY)
public class BuilderHandler extends BaseHandler<Builder> {
    
    public static final int PRIORITY = ConstructorHandler.PRIORITY << 2;
    
    @Override
    public void processMethod(final PsiMethod tree, final Builder annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) {
        if (!tree.isConstructor())
            return;
        final LightClass builderClass = { tree, "InnerBuilder", STR."\{context.getQualifiedName()}.InnerBuilder", annotationTree };
        Stream.of(context.getTypeParameters()).forEach(builderClass.getTypeParameterList()::addParameter);
        if (members.shouldInject(builderClass)) {
            builderClass.setNavigationElement(annotationTree);
            builderClass.setContainingClass(context);
            builderClass.addModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC);
            followAnnotation(annotationTree, "on", builderClass.getModifierList());
            final LightMethod buildMethod = { tree, "build", annotationTree };
            buildMethod.setMethodReturnType(typeWithGenerics(context));
            Stream.of(tree.getThrowsList().getReferencedTypes()).forEach(buildMethod.getThrowsList()::addReference);
            followAnnotation(tree.getModifierList(), buildMethod.getModifierList());
            buildMethod.setNavigationElement(tree);
            buildMethod.setContainingClass(builderClass);
            buildMethod.addModifier(PsiModifier.PUBLIC);
            buildMethod.setMethodKind(handler().value().getCanonicalName());
            Stream.of(tree.getParameterList().getParameters())
                    .map(parameter -> new LightField(parameter, parameter.getName(), parameter.getType(), annotationTree))
                    .peek(builderClass::addField)
                    .forEach(field -> {
                        final LightMethod withMethod = { field, field.getName(), annotationTree };
                        withMethod.setMethodReturnType(typeWithGenerics(builderClass));
                        withMethod.addParameter(field.getName(), field.getType(), false);
                        withMethod.setNavigationElement(tree);
                        withMethod.setContainingClass(builderClass);
                        withMethod.addModifier(PsiModifier.PUBLIC);
                        withMethod.setMethodKind(handler().value().getCanonicalName());
                        builderClass.addMethod(withMethod);
                    });
            builderClass.addMethod(buildMethod);
            followAnnotation(tree.getModifierList(), builderClass.getModifierList());
            members.inject(builderClass);
        }
        final LightMethod builderMethod = { tree, "builder", annotationTree };
        Stream.of(context.getTypeParameters()).forEach(builderMethod::addTypeParameter);
        builderMethod.setMethodReturnType(typeWithGenerics(builderClass));
        if (members.shouldInject(builderMethod)) {
            builderMethod.setNavigationElement(annotationTree);
            builderMethod.setContainingClass(context);
            builderMethod.addModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC);
            followAnnotation(tree.getModifierList(), builderMethod.getModifierList());
            builderMethod.setMethodKind(handler().value().getCanonicalName());
            members.inject(builderMethod);
        }
    }
    
}
