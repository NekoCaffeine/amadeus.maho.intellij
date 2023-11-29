package amadeus.maho.lang.idea;

import com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface DisableBytecodeAnalysis {
    
    String ENABLE_BYTECODE_ANALYSIS = "maho.intellij.enable.bytecode.analysis";
    
    @Hook(forceReturn = true, metadata = @TransformMetadata(disable = ENABLE_BYTECODE_ANALYSIS))
    private static @Nullable PsiAnnotation findInferredAnnotation(final ProjectBytecodeAnalysis $this, final PsiModifierListOwner owner, final String fqn) = null;
    
    @Hook(forceReturn = true, metadata = @TransformMetadata(disable = ENABLE_BYTECODE_ANALYSIS))
    private static PsiAnnotation[] findInferredAnnotations(final ProjectBytecodeAnalysis $this, final PsiModifierListOwner owner) = PsiAnnotation.EMPTY_ARRAY;
    
}
