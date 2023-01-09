package amadeus.maho.lang.idea;

import java.util.LinkedList;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.FileContent;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

// java.lang.AssertionError: Reentrant indexing
// 	at com.intellij.util.indexing.FileBasedIndexImpl.markFileIndexed(FileBasedIndexImpl.java:1710)
@TransformProvider
public interface ReentrantIndexing {
    
    ThreadLocal<LinkedList<VirtualFile>> indexFiles = ThreadLocal.withInitial(LinkedList::new);
    
    static LinkedList<VirtualFile> indexFiles() = indexFiles.get();
    
    @Hook(value = FileBasedIndexImpl.class, isStatic = true)
    private static Hook.Result markFileIndexed(final @Nullable VirtualFile file, final @Nullable FileContent content) {
        indexFiles() << file;
        return Hook.Result.NULL;
    }
    
    @Hook(value = FileBasedIndexImpl.class, isStatic = true)
    private static Hook.Result unmarkBeingIndexed() {
        indexFiles()--;
        return Hook.Result.NULL;
    }
    
    @Hook
    private static Hook.Result getFileBeingCurrentlyIndexed(final FileBasedIndexImpl $this) = { indexFiles().peekLast() };
    
}
