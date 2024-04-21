package amadeus.maho.lang.idea;

import com.intellij.util.indexing.projectFilter.ProjectIndexableFilesFilterHealthCheck;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface DisableHealthCheck {

    @Hook(forceReturn = true)
    private static void setUpHealthCheck(final ProjectIndexableFilesFilterHealthCheck $this) { }

    @Hook(forceReturn = true)
    private static void runHealthCheck(final ProjectIndexableFilesFilterHealthCheck $this) { }

}
