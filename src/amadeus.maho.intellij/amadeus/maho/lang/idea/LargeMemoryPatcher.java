package amadeus.maho.lang.idea;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.util.ConcurrencyUtil;

import amadeus.maho.lang.Privilege;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.ReferenceCollector;

public interface LargeMemoryPatcher {
    
    @TransformProvider
    interface FileManagerPatcher {
        
        @Hook
        private static Hook.Result getVFileToViewProviderMap(final FileManagerImpl $this) {
            final AtomicReference<ConcurrentMap<VirtualFile, FileViewProvider>> myVFileToViewProviderMap = (Privilege) $this.myVFileToViewProviderMap;
            ConcurrentMap<VirtualFile, FileViewProvider> map = myVFileToViewProviderMap.get();
            if (map == null) {
                map = ConcurrencyUtil.cacheOrGet(myVFileToViewProviderMap, new ConcurrentHashMap<>());
            }
            return { map };
        }
        
        @Hook
        private static Hook.Result getVFileToPsiDirMap(final FileManagerImpl $this) {
            final AtomicReference<ConcurrentMap<VirtualFile, PsiDirectory>> myVFileToPsiDirMap = (Privilege) $this.myVFileToPsiDirMap;
            ConcurrentMap<VirtualFile, PsiDirectory> map = myVFileToPsiDirMap.get();
            if (map == null)
                map = ConcurrencyUtil.cacheOrGet(myVFileToPsiDirMap, new ConcurrentHashMap<>());
            return { map };
        }
        
    }
    
    ReferenceCollector.Base collector = new ReferenceCollector.Base().let(ReferenceCollector.Base::start);
    
}
