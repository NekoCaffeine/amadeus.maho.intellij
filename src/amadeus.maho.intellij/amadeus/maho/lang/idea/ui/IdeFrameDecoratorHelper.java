package amadeus.maho.lang.idea.ui;

import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import javax.swing.JRootPane;
import javax.swing.RootPaneContainer;
import javax.swing.border.Border;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.IdeFrameDecorator;
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomHeader;
import com.intellij.platform.ide.bootstrap.Splash;
import com.intellij.ui.AppIcon;
import com.intellij.ui.AppUIUtilKt;
import com.intellij.ui.WindowMoveListener;
import com.intellij.ui.WindowResizeListener;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface IdeFrameDecoratorHelper {
    
    static boolean shouldEnable() = !SystemInfo.isJetBrainsJvm && SystemInfo.isWindows; // The compatibility of OpenJDK and IDEA on other platforms has not been tested yet, TODO [low] test other platforms
    
    @Hook(value = AppIcon.class, isStatic = true)
    private static void getInstance() {
        if ((Privilege) AppIcon.ourIcon == null)
            (Privilege) (AppIcon.ourIcon = (Privilege) new AppIcon.EmptyIcon());
    }
    
    // #IC-233.9102.97 2023.3 EAP
    @SuppressWarnings("Hook")
    @Hook(target = "com.intellij.openapi.wm.impl.WinMainFrameDecorator$toggleFullScreen$2",
            at = @At(method = @At.MethodInsn(name = "dispose"), offset = -3), jump = @At(method = @At.MethodInsn(name = "setUndecorated"), offset = 1), exactMatch = false)
    private static Hook.Result invokeSuspend() = shouldEnable() ? new Hook.Result().jump() : Hook.Result.VOID;
    
    @Hook
    private static Hook.Result isCustomDecorationAvailable$intellij_platform_ide_impl(final IdeFrameDecorator.Companion $this) = Hook.Result.falseToVoid(shouldEnable());
    
    @Hook
    private static Hook.Result isCustomDecorationActive(final IdeFrameDecorator.Companion $this) = Hook.Result.falseToVoid(shouldEnable());
    
    @Hook
    private static Hook.Result setUndecorated(final Frame $this, final boolean flag) = Hook.Result.falseToVoid(shouldEnable() && !flag && $this instanceof RootPaneContainer, null);
    
    @Hook
    private static void addNotify(final Frame $this) = decoration($this);
    
    @Hook
    private static void addNotify(final Dialog $this) = decoration($this);
    
    static JBEmptyBorder outerBorder() = { 0 };
    
    private static void decoration(final Window window) {
        if (!window.isDisplayable() && shouldEnable()) {
            AppUIUtilKt.updateAppWindowIcon(window);
            if (window instanceof RootPaneContainer container) {
                final @Nullable JRootPane rootPane = container.getRootPane();
                if (rootPane != null) {
                    switch (window) {
                        case Frame frame   -> frame.setUndecorated(true);
                        case Dialog dialog -> dialog.setUndecorated(true);
                        default            -> { }
                    }
                    final Border border = outerBorder();
                    rootPane.setBorder(border);
                    final WindowResizeListener resizeListener = { rootPane, JBUI.insets(10), null };
                    window.addMouseListener(resizeListener);
                    window.addMouseMotionListener(resizeListener);
                    rootPane.addMouseListener(resizeListener);
                    rootPane.addMouseMotionListener(resizeListener);
                    final @Nullable Container contentPane = container.getContentPane();
                    contentPane?.addMouseListener(resizeListener);
                    contentPane?.addMouseMotionListener(resizeListener);
                }
            } else if (window instanceof Splash splash)
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
