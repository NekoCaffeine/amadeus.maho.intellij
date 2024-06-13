package amadeus.maho.lang.idea.handler;

import java.util.List;
import java.util.stream.Stream;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.impl.source.PsiExtensibleClass;

import amadeus.maho.lang.Include;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.ExtensibleMembers;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.light.LightBridgeElement;
import amadeus.maho.lang.idea.light.LightBridgeField;
import amadeus.maho.lang.idea.light.LightBridgeMethod;
import amadeus.maho.util.runtime.DebugHelper;

import static amadeus.maho.lang.idea.handler.IncludeHandler.PRIORITY;

@Handler(value = Include.class, priority = PRIORITY)
public class IncludeHandler extends BaseHandler<Include> {
    
    public static final int PRIORITY = DelegateHandler.PRIORITY << 2;
    
    @Override
    public boolean contextFilter(final PsiClass context) = true;
    
    @Override
    public void processClass(final PsiClass tree, final Include annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) {
        if (tree != context)
            return;
        final List<? extends LightBridgeElement> injectBridgeElements = annotation.accessPsiClasses(Include::value)
                .map(PsiClassType::resolve)
                .nonnull()
                .cast(PsiExtensibleClass.class)
                .flatMap(includeClass -> Stream.concat(includeClass.getOwnFields().stream(), includeClass.getOwnMethods().stream())
                        .filter(member -> member.hasModifierProperty(PsiModifier.PUBLIC) && member.hasModifierProperty(PsiModifier.STATIC))
                        .filter(members::shouldInject)
                        .map(member -> (LightBridgeElement) switch (member) {
                            case PsiMethod method -> new LightBridgeMethod(context, method, PsiSubstitutor.EMPTY, annotationTree, method)
                                    .addModifier(PsiModifier.PUBLIC)
                                    .addModifier(PsiModifier.STATIC)
                                    .let(it -> it.setMethodKind(handler().value().getCanonicalName()));
                            case PsiField field   -> new LightBridgeField(context, field.getName(), field.getType(), annotationTree, field)
                                    .addModifier(PsiModifier.PUBLIC)
                                    .addModifier(PsiModifier.STATIC);
                            default               -> throw DebugHelper.breakpointBeforeThrow(new UnsupportedOperationException(STR."member: \{member}"));
                        }))
                .toList();
        members.injectBridgeProvider((_, _) -> injectBridgeElements);
    }
    
}
