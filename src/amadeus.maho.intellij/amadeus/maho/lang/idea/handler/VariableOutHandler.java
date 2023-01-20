package amadeus.maho.lang.idea.handler;

import java.lang.annotation.Annotation;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiVariable;

import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.handler.base.HandlerMarker;
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
    private static Hook.Result reportAssignmentProblem(final PsiVariable variable, final PsiAssignmentExpression assignment, final ProblemsHolder holder) = Hook.Result.falseToVoid(
            HandlerMarker.Marker.baseHandlers().stream().anyMatch(handler -> handler.isVariableOut(variable)) || HandlerMarker.SyntaxMarker.syntaxHandlers().values().stream().anyMatch(handler -> handler.isVariableOut(variable)));
    
}