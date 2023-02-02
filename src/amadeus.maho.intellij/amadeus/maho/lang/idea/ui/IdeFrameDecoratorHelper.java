package amadeus.maho.lang.idea.ui;

import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Shape;
import java.awt.Window;
import java.util.List;
import java.util.Map;
import javax.swing.JRootPane;
import javax.swing.RootPaneContainer;
import javax.swing.border.Border;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.IdeFrameDecorator;
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomHeader;
import com.intellij.ui.AppIcon;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.Splash;
import com.intellij.ui.WindowMoveListener;
import com.intellij.ui.WindowResizeListener;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

import com.jetbrains.CustomWindowDecoration;
import com.jetbrains.JBR;

@TransformProvider
public interface IdeFrameDecoratorHelper {
    
    // JBR support, module: jetbrains.api, location: lib/app.jar
    enum WithoutJBRWindowDecoration implements CustomWindowDecoration {
        
        @Getter
        instance;
        
        @Override
        public void setCustomDecorationEnabled(final Window window, final boolean enabled) { }
        
        @Override
        public boolean isCustomDecorationEnabled(final Window window) = true;
        
        @Override
        public void setCustomDecorationHitTestSpots(final Window window, final List<Map.Entry<Shape, Integer>> spots) { }
        
        @Override
        public List<Map.Entry<Shape, Integer>> getCustomDecorationHitTestSpots(final Window window) = List.of();
        
        @Override
        public void setCustomDecorationTitleBarHeight(final Window window, final int height) { }
        
        @Override
        public int getCustomDecorationTitleBarHeight(final Window window) = 0;
        
    }
    
    static boolean shouldEnable() = !SystemInfo.isJetBrainsJvm && SystemInfo.isWindows; // The compatibility of OpenJDK and IDEA on other platforms has not been tested yet, TODO [low] test other platforms
    
    @Hook(value = AppIcon.class, isStatic = true)
    private static void getInstance() {
        if ((Privilege) AppIcon.ourIcon == null)
            (Privilege) (AppIcon.ourIcon = (Privilege) new AppIcon.EmptyIcon());
    }
    
    @Hook(value = JBR.class, isStatic = true)
    private static Hook.Result getCustomWindowDecoration() = Hook.Result.falseToVoid(shouldEnable(), WithoutJBRWindowDecoration.instance());
    
    // #IC-223.6160.11 2022.3 EAP
    @Hook(at = @At(method = @At.MethodInsn(name = "dispose"), offset = -2), jump = @At(method = @At.MethodInsn(name = "setUndecorated"), offset = 1), exactMatch = false)
    private static Hook.Result toggleFullScreen(final IdeFrameDecorator.WinMainFrameDecorator $this) = shouldEnable() ? new Hook.Result().jump() : Hook.Result.VOID;
    
    @Hook(value = IdeFrameDecorator.class, isStatic = true)
    private static Hook.Result isCustomDecorationAvailable() = Hook.Result.falseToVoid(shouldEnable());
    
    @Hook(value = IdeFrameDecorator.class, isStatic = true)
    private static Hook.Result isCustomDecorationActive() = Hook.Result.falseToVoid(shouldEnable());
    
    @Hook
    private static Hook.Result setUndecorated(final Frame $this, final boolean flag) = Hook.Result.falseToVoid(shouldEnable() && !flag && $this instanceof RootPaneContainer, null);
    
    @Hook
    private static void addNotify(final Frame $this) = decoration($this);
    
    @Hook
    private static void addNotify(final Dialog $this) = decoration($this);
    
    static JBEmptyBorder outerBorder() = { 2 };
    
    private static void decoration(final Window window) {
        if (!window.isDisplayable() && shouldEnable()) {
            AppUIUtil.updateWindowIcon(window);
            if (window instanceof final RootPaneContainer container) {
                switch (window) {
                    case Frame frame   -> frame.setUndecorated(true);
                    case Dialog dialog -> dialog.setUndecorated(true);
                    default            -> { }
                }
                final Border border = outerBorder();
                final @Nullable JRootPane rootPane = container.getRootPane();
                rootPane?.setBorder(border);
                final @Nullable Container contentPane = container.getContentPane();
                final WindowResizeListener resizeListener = { rootPane, JBUI.insets(10), null };
                window.addMouseListener(resizeListener);
                window.addMouseMotionListener(resizeListener);
                rootPane?.addMouseListener(resizeListener);
                rootPane?.addMouseMotionListener(resizeListener);
                contentPane?.addMouseListener(resizeListener);
                contentPane?.addMouseMotionListener(resizeListener);
            } else if (window instanceof final Splash splash)
                splash.setUndecorated(true);
        }
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void _init_(final CustomHeader $this, final Window window) {
        if (shouldEnable()) {
            final WindowMoveListener moveListener = { window };
            $this.addMouseListener(moveListener);
            $this.addMouseMotionListener(moveListener);
        }
    }
    
}
