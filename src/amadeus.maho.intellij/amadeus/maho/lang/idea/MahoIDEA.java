package amadeus.maho.lang.idea;

import java.awt.EventQueue;
import java.net.URL;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.plugins.cl.PluginClassLoader;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoExport;
import amadeus.maho.core.extension.ReflectBreaker;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.handler.ShareMarker;
import amadeus.maho.util.resource.ResourcePath;
import amadeus.maho.util.runtime.DebugHelper;

@SneakyThrows
public final class MahoIDEA implements AppLifecycleListener {
    
    private static Predicate<URL> sourceChecker(final Class<?> target) {
        final Function<URL, String> name = url -> {
            final String file = url.getFile();
            final int index = file.lastIndexOf('/');
            return index == -1 ? file : file.substring(index + 1);
        };
        if (target.getName().startsWith("amadeus.maho.lang.idea."))
            return url -> name.apply(url).startsWith("amadeus.maho.intellij-");
        if (target.getName().startsWith("amadeus.maho."))
            return url -> name.apply(url).startsWith("amadeus.maho-");
        throw new IllegalArgumentException(target.getName());
    }
    
    static {
        try {
            new Object() {{ // avoid lambda init deadlock
                LargeMemoryPatcher.extendWorkingSetSize();
                ResourcePath.classMapperChain().add(target -> target
                        .stream()
                        .map(Class::getClassLoader)
                        .filter(PluginClassLoader.class::isInstance)
                        .map(PluginClassLoader.class::cast)
                        .map(PluginClassLoader::getUrls)
                        .flatMap(List::stream)
                        .filter(sourceChecker(target.orElseThrow()))
                        .findFirst());
                MahoExport.Setup.minimize();
                ShareMarker.whenShare(HookResultInjector.HOOK_RESULT_NAME, () -> Maho.inject(HookResultInjector.instance()));
                Maho.setupFromClass();
                ReflectBreaker.jailbreak();
                ObjectTreeInjector.inject();
                final @Nullable Thread edt = EDTWatcher.lookupThread("AWT-EventQueue-0");
                if (edt != null) {
                    final EDTWatcher watcher = { edt, future -> EventQueue.invokeLater(() -> future.complete(null)) };
                    watcher.start();
                }
            }};
        } catch (final Throwable throwable) {
            DebugHelper.breakpoint();
            throwable.printStackTrace();
        }
    }
    
}
