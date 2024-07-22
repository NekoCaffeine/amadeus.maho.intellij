package amadeus.maho.lang.idea.handler.base;

import java.util.BitSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;
import com.intellij.codeInspection.dataFlow.DataFlowInspectionBase;
import com.intellij.codeInspection.dataFlow.DataFlowInstructionVisitor;
import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.codeInspection.dataFlow.MutationSignature;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.dataFlow.inference.JavaSourceInference;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiInstanceOfExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.util.CachedValuesManager;

import amadeus.maho.lang.Privilege;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

import static amadeus.maho.lang.idea.handler.NullableHandler.*;
import static com.intellij.codeInspection.dataFlow.NullabilityProblemKind.assigningToNonAnnotatedField;

@TransformProvider
public interface DFAAdjuster {
    
    @Hook(forceReturn = true)
    private static void reportStreamConsumed(final DataFlowInspectionBase $this, final ProblemsHolder holder, final DataFlowInstructionVisitor visitor) = ((Privilege) visitor.streamConsumed()).forKeyValue((psiElement, alwaysFails) -> {
        if (alwaysFails)
            holder.registerProblem(psiElement, JavaAnalysisBundle.message("dataflow.message.stream.consumed.always"));
    });
    
    @Hook(value = JavaSourceInference.class, isStatic = true, forceReturn = true)
    private static JavaSourceInference.MethodInferenceData getInferenceData(final PsiMethod method) = method instanceof PsiMethodImpl ? CachedValuesManager.getProjectPsiDependentCache(method, it -> {
        final Nullability defaultNullability = defaultNullability(method);
        final PsiParameter parameters[] = method.getParameterList().getParameters();
        final BitSet notNullParameters = { };
        IntStream.range(0, parameters.length).forEach(index -> {
            if (findNullability(parameters[index], defaultNullability) == Nullability.NOT_NULL)
                notNullParameters.set(index);
        });
        return (Privilege) new JavaSourceInference.MethodInferenceData(
                Mutability.UNKNOWN, findNullability(method, defaultNullability), List.of(), (Privilege) MutationSignature.UNKNOWN, notNullParameters);
    }) : (Privilege) JavaSourceInference.MethodInferenceData.UNKNOWN;
    
    @Hook(value = ConstantValueInspection.class, isStatic = true)
    private static Hook.Result shouldBeSuppressed(final PsiElement anchor) = Hook.Result.falseToVoid(anchor instanceof PsiInstanceOfExpression instanceOfExpression && instanceOfExpression.getPattern() != null);
    
    @Hook
    private static <T extends PsiElement> Hook.Result ifMyProblem(final NullabilityProblemKind<T> $this, final NullabilityProblemKind.NullabilityProblem<?> problem, final Consumer<? super T> consumer) {
        if ($this == assigningToNonAnnotatedField) {
            final NullabilityProblemKind.NullabilityProblem<T> myProblem = $this.asMyProblem(problem);
            if (myProblem != null && (Privilege) DataFlowInspectionBase.getAssignedField(myProblem.getAnchor()) instanceof PsiField field && defaultNullability(field) != Nullability.NOT_NULL)
                return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
}
