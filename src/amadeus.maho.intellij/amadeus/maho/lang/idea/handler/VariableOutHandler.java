package amadeus.maho.lang.idea.handler;

import java.lang.annotation.Annotation;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiVariable;

import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.handler.base.Syntax;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class VariableOutHandler<A extends Annotation> extends BaseHandler<A> {
    
    @Handler(Hook.Reference.class)
    public static final class HookReferenceHandler extends VariableOutHandler<Hook.Reference> {
        
        @Override
        public boolean isVariableOut(final PsiVariable variable) = variable instanceof PsiParameter && super.isVariableOut(variable);
        
    }
    
    @Override
    public boolean isVariableOut(final PsiVariable variable) = variable.hasAnnotation(handler().value().getCanonicalName());
    
    @Hook(value = DefUseInspection.class, isStatic = true)
    private static Hook.Result reportInitializerProblem(final PsiVariable variable, final ProblemsHolder holder) = Hook.Result.falseToVoid(
            Handler.Marker.baseHandlers().stream().anyMatch(handler -> handler.isVariableOut(variable)) || Syntax.Marker.syntaxHandlers().values().stream().anyMatch(handler -> handler.isVariableOut(variable)));
    
}
