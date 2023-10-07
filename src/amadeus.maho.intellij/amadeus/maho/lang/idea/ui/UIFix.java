package amadeus.maho.lang.idea.ui;

import java.awt.Color;
import java.util.Set;
import javax.swing.UIDefaults;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface UIFix {
    
    Set<String> menuColorFallbacks = Set.of("MainMenu.background", "MenuItem.background");
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static Color getColor(final Color capture, final UIDefaults $this, final Object key) {
        if (menuColorFallbacks.contains(key))
            return (Color) $this.get("Menu.background");
        return capture;
    }
    
}
