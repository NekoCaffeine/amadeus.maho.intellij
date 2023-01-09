package amadeus.maho.lang.idea;

import com.intellij.util.indexing.CorruptionMarker;
import com.intellij.util.indexing.FileBasedIndex;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface InMemoryIndex {
    
    boolean enable = FileBasedIndex.USE_IN_MEMORY_INDEX && !Boolean.getBoolean("amadeus.maho.intellij.disable.index.fix");
    
    @Hook(value = CorruptionMarker.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static boolean requireInvalidation(final boolean capture) = capture || enable;
    
}
