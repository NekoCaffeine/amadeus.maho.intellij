package amadeus.maho.lang.idea.handler;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.psi.PsiRecordComponent;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class RecordHandler {
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkRecordComponentCStyleDeclaration(final PsiRecordComponent component) = Hook.Result.NULL;
    
}
