package amadeus.maho.lang.idea;

import java.net.URL;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import com.intellij.ide.plugins.cl.PluginClassLoader;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoExport;
import amadeus.maho.core.extension.ReflectBreaker;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.resource.ResourcePath;

@SneakyThrows
final class MahoIDEA {
    
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
    
    // public static final Sampler.Interceptor interceptor = { };
    
    static {
        try {
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
            Maho.setupFromClass();
            ReflectBreaker.jailbreak();
            ObjectTreeInjector.inject();
            // async(() -> InterceptorManager.instance().install(() -> interceptor, (loader, name) -> name.startsWith("amadeus.maho.lang.idea.handler.base.HandlerMarker")));
        } catch (final Throwable throwable) { throwable.printStackTrace(); }
    }
    
}
