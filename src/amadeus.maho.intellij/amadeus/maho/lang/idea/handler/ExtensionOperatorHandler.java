package amadeus.maho.lang.idea.handler;

import java.util.stream.Stream;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.ExtensibleMembers;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.light.LightBridgeMethod;
import amadeus.maho.lang.idea.light.LightMethod;

import static amadeus.maho.lang.idea.IDEAContext.OperatorData.operatorSymbol2operatorName;
import static amadeus.maho.lang.idea.handler.SetterHandler.PRIORITY;

@Handler(value = Extension.Operator.class, priority = PRIORITY)
public class ExtensionOperatorHandler extends BaseHandler<Extension.Operator> {
    
    public static final int PRIORITY = ReferenceHandler.PRIORITY >> 2;
    
    @Override
    public void processMethod(final PsiMethod tree, final Extension.Operator annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) {
        if (tree instanceof LightBridgeMethod)
            return;
        final LightMethod methodTree = { context, tree, PsiSubstitutor.EMPTY, annotationTree, tree };
        methodTree.name(operatorSymbol2operatorName.getOrDefault(annotation.value(), annotation.value()));
        if (members.shouldInject(methodTree)) {
            Stream.of(PsiModifier.ABSTRACT, PsiModifier.NATIVE, PsiModifier.SYNCHRONIZED).forEach(methodTree.getModifierList().modifiers()::remove);
            if (context.isInterface())
                methodTree.getModifierList().modifiers().add(PsiModifier.DEFAULT);
            members.inject(methodTree);
        }
    }
    
}
