package amadeus.maho.lang.idea;

import com.intellij.formatting.visualLayer.VisualFormattingLayerService;
import com.intellij.openapi.editor.Editor;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface DisableVisualFormattingLayerService {

    @Hook(value = VisualFormattingLayerService.class, isStatic = true, forceReturn = true)
    private static boolean shouldRemoveZombieFoldings() = true;

    @Hook(value = VisualFormattingLayerService.class, isStatic = true, forceReturn = true)
    private static boolean isEnabledForEditor(final Editor editor) = false;

}
