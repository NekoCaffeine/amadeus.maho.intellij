package amadeus.maho.lang.idea;

import java.util.List;

import com.intellij.formatting.visualLayer.VisualFormattingLayerElement;
import com.intellij.formatting.visualLayer.VisualFormattingLayerService;
import com.intellij.formatting.visualLayer.VisualFormattingLayerServiceImpl;
import com.intellij.openapi.editor.Editor;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface DisableVisualFormattingLayer {
    
    @Hook(forceReturn = true)
    private static List<VisualFormattingLayerElement> collectVisualFormattingLayerElements(final VisualFormattingLayerServiceImpl $this, final Editor editor) = List.of();
    
    @Hook(value = VisualFormattingLayerService.class, isStatic = true, forceReturn = true)
    private static boolean shouldRemoveZombieFoldings() = true;
    
    @Hook(value = VisualFormattingLayerService.class, isStatic = true, forceReturn = true)
    private static boolean isEnabledForEditor(final Editor editor) = false;
    
}
