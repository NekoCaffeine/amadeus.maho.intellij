package amadeus.maho.lang.idea;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.ui.ClickListener;
import com.intellij.util.ConcurrencyUtil;

import amadeus.maho.lang.Privilege;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.ReferenceCollector;

public interface LargeMemoryPatcher {
    
    @TransformProvider
    interface FileManagerPatcher {
        
        @Hook
        private static Hook.Result getVFileToViewProviderMap(final FileManagerImpl $this) {
            final AtomicReference<ConcurrentMap<VirtualFile, FileViewProvider>> myVFileToViewProviderMap = (Privilege) $this.myVFileToViewProviderMap;
            ConcurrentMap<VirtualFile, FileViewProvider> map = myVFileToViewProviderMap.get();
            if (map == null)
                map = ConcurrencyUtil.cacheOrGet(myVFileToViewProviderMap, new ConcurrentHashMap<>());
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
        
        @Hook(exactMatch = false)
        private static void onClick(final @InvisibleType("com.intellij.openapi.wm.impl.status.MemoryUsagePanel$1") ClickListener $this) {
            Stream.of(ProjectManager.getInstance().getOpenProjects())
                    .map(PsiManager::getInstance)
                    .cast(PsiManagerEx.class)
                    .map(PsiManagerEx::getFileManager)
                    .cast(FileManagerImpl.class)
                    .forEach(manager -> (Privilege) manager.processFileTypesChanged(true));
        }
        
    }
    
    ReferenceCollector.Base collector = new ReferenceCollector.Base().let(ReferenceCollector.Base::start);
    
}
