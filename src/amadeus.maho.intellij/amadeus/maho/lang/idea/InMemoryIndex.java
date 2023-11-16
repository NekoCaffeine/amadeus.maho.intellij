package amadeus.maho.lang.idea;

import java.io.IOException;

import com.intellij.openapi.project.Project;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.indexing.CorruptionMarker;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileBasedIndexInfrastructureExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.UpdatableIndex;
import com.intellij.util.indexing.impl.storage.VfsAwareMapReduceIndex;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface InMemoryIndex {
    
    class InfrastructureExtension implements FileBasedIndexInfrastructureExtension {
        
        @Override
        public @Nullable FileIndexingStatusProcessor createFileIndexingStatusProcessor(final Project project) = null;
        
        @Override
        public @Nullable <K, V> UpdatableIndex<K, V, FileContent, ?> combineIndex(final FileBasedIndexExtension<K, V> indexExtension, final UpdatableIndex<K, V, FileContent, ?> baseIndex) throws IOException = null;
        
        @Override
        public void onFileBasedIndexVersionChanged(final ID<?, ?> indexId) { }
        
        @Override
        public void onStubIndexVersionChanged(final StubIndexKey<?, ?> indexId) { }
        
        @Override
        public InitializationResult initialize(final @Nullable("null if default") String indexLayoutId) = InitializationResult.INDEX_REBUILD_REQUIRED;
        
        @Override
        public void resetPersistentState() { }
        
        @Override
        public void resetPersistentState(final ID<?, ?> indexId) { }
        
        @Override
        public void shutdown() { }
        
        @Override
        public int getVersion() = -1;
        
    }
    
    boolean enable = FileBasedIndex.USE_IN_MEMORY_INDEX && !Boolean.getBoolean("amadeus.maho.intellij.disable.index.fix");
    
    @Hook(value = CorruptionMarker.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static boolean requireInvalidation(final boolean capture) = capture || enable;
    
    @Hook
    private static <Key, Value, FileCachedData extends VfsAwareMapReduceIndex.IndexerIdHolder> Hook.Result getStoredFileSubIndexerId(final VfsAwareMapReduceIndex<Key, Value, FileCachedData> $this, final int fileId)
            = Hook.Result.falseToVoid((Privilege) $this.mySubIndexerRetriever == null, -1);
    
}
