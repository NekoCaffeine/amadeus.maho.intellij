package amadeus.maho.lang.idea.handler;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiResourceVariable;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.TypeConversionUtil;

import amadeus.maho.lang.idea.handler.base.BaseSyntaxHandler;
import amadeus.maho.lang.idea.handler.base.ImplicitUsageChecker;
import amadeus.maho.lang.idea.handler.base.Syntax;
import amadeus.maho.lang.mark.StateSnapshot;
import amadeus.maho.transform.mark.base.TransformProvider;

import static amadeus.maho.lang.idea.handler.StateSnapshotHandler.PRIORITY;

@TransformProvider
@Syntax(priority = PRIORITY)
public class StateSnapshotHandler extends BaseSyntaxHandler {
    
    public static final int PRIORITY = 1 << 20;
    
    public static boolean isStateSnapshot(final PsiVariable variable) = variable instanceof PsiResourceVariable resourceVariable && TypeConversionUtil.isAssignable(
            PsiType.getTypeByName(StateSnapshot.class.getCanonicalName(), resourceVariable.getProject(), resourceVariable.getResolveScope()), variable.getType());
    
    @Override
    public boolean isVariableOut(final PsiVariable variable) = isStateSnapshot(variable);
    
    @Override
    public boolean isImplicitUsage(final PsiElement tree, final ImplicitUsageChecker.RefData refData) = tree instanceof PsiVariable variable && isStateSnapshot(variable);
    
    @Override
    public boolean isImplicitRead(final PsiElement tree, final ImplicitUsageChecker.RefData refData) = isImplicitUsage(tree, refData);
    
}
