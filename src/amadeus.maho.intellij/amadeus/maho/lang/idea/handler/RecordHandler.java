package amadeus.maho.lang.idea.handler;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.search.JavaRecordComponentSearcher;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class RecordHandler {
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkRecordComponentCStyleDeclaration(final PsiRecordComponent component) = Hook.Result.NULL;
    
    @Hook(at = @At(method = @At.MethodInsn(name = "compute"), offset = 1, ordinal = 0), before = false, capture = true)
    private static SearchScope processQuery(final SearchScope capture, final JavaRecordComponentSearcher $this, final ReferencesSearch.SearchParameters parameters, final Processor<? super PsiReference> consumer)
            = parameters.getScopeDeterminedByUser().intersectWith(capture);
    
}
