package amadeus.maho.lang.idea.ui;

import java.util.Optional;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;

import io.unthrottled.doki.icons.jetbrains.tree.NamedIconMapping;
import io.unthrottled.doki.icons.jetbrains.tree.NamedMappingStore;
import io.unthrottled.doki.icons.jetbrains.tree.OptimisticNameProvider;

@TransformProvider
public interface DokiIconsFix {
    
    ConcurrentWeakIdentityHashMap<String, Optional<NamedIconMapping>> cache = { };
    
    @Hook(forceReturn = true)
    private static Optional<NamedIconMapping> findMapping(final OptimisticNameProvider $this, final String name)
        = cache.computeIfAbsent(name, it -> NamedMappingStore.INSTANCE.getFILES().stream().filter(mapping -> mapping.getMappingRegex().matches(it)).findFirst());
    
}
