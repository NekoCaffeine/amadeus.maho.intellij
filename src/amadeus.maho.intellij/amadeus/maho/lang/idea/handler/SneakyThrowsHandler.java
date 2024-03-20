package amadeus.maho.lang.idea.handler;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiResourceListElement;
import com.intellij.psi.controlFlow.ControlFlowAnalyzer;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.CheckedExceptionCompatibilityConstraint;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.ConstraintFormula;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Redirect;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.Slice;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class SneakyThrowsHandler {
    
    @Hook(value = HighlightUtil.class, isStatic = true, forceReturn = true)
    public static @Nullable HighlightInfo.Builder checkSimpleCatchParameter(final PsiParameter parameter, final Collection<? extends PsiClassType> thrownTypes, final PsiClassType caughtType) = null;
    
    @Hook(value = HighlightUtil.class, isStatic = true, forceReturn = true)
    public static void checkMultiCatchParameter(final PsiParameter parameter, final Collection<? extends PsiClassType> thrownTypes, final Consumer<? super HighlightInfo.Builder> errorSink) { }
    
    public static boolean isHandled(PsiElement element) {
        while (element != null && !(element instanceof PsiFile)) {
            if (element instanceof PsiModifierListOwner owner && owner.hasAnnotation(SneakyThrows.class.getCanonicalName()))
                return true;
            element = element.getParent();
        }
        return false;
    }
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    public static Hook.Result checkUnhandledExceptions(final PsiElement element) = Hook.Result.falseToVoid(isHandled(element), null);
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    public static Hook.Result checkUnhandledCloserExceptions(final PsiResourceListElement element) = Hook.Result.falseToVoid(isHandled(element), null);
    
    @Hook(at = @At(method = @At.MethodInsn(name = "isAddressed")), capture = true, before = false, branchReversal = true)
    public static boolean reduce(final boolean result, final CheckedExceptionCompatibilityConstraint $this, final InferenceSession session, final List<ConstraintFormula> constraints) = result || isHandled($this.getExpression());
    
    @Redirect(targetClass = ControlFlowAnalyzer.class, selector = "visitTryStatement", slice = @Slice(@At(method = @At.MethodInsn(name = "isUncheckedExceptionOrSuperclass"))))
    private static boolean isUncheckedExceptionOrSuperclass(final PsiClassType classType) = true;
    
}
