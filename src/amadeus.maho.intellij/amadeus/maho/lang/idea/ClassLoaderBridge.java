package amadeus.maho.lang.idea;

import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.util.lang.UrlClassLoader;

import amadeus.maho.lang.Privilege;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface ClassLoaderBridge {
    
    ClassLoader loader = MahoIDEA.class.getClassLoader();
    
    String HOOK_RESULT = "amadeus.maho.transform.mark$Hook";
    
    ThreadLocal<Boolean> forkLoadClassMark = ThreadLocal.withInitial(() -> false);
    
    @Hook
    private static Hook.Result findClass(final UrlClassLoader $this, final String name) = tryLoadMahoClass($this, name);
    
    @Hook(avoidRecursion = true)
    private static Hook.Result loadClass(final PluginClassLoader $this, final String name, final boolean resolve) = tryLoadMahoClass($this, name);
    
    private static Hook.Result tryLoadMahoClass(final UrlClassLoader $this, final String name) {
        if (name.equals(HOOK_RESULT))
            return { (Privilege) ClassLoader.findBootstrapClass(HOOK_RESULT) };
        if (forkLoadClassMark.get() || $this == loader || !name.startsWith("amadeus.maho."))
            return Hook.Result.VOID;
        forkLoadClassMark.set(true);
        try {
            return { loader.fastLookup(name) };
        } finally { forkLoadClassMark.set(false); }
    }
    
}
