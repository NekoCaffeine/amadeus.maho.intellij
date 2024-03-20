package amadeus.maho.lang.idea;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.impl.source.FileTrees;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.stubs.StubStringInterner;
import com.intellij.psi.stubs.StubTree;
import com.intellij.psi.stubs.StubTreeLoader;
import com.intellij.ui.ClickListener;
import com.intellij.util.ConcurrencyUtil;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.transform.mark.base.TransformProvider;

public interface LargeMemoryPatcher {
    
    @TransformProvider
    interface Layer {
        
        @Hook(forceReturn = true)
        private static String apply(final StubStringInterner $this, final String str) = str;
        
        @Hook(forceReturn = true)
        private static boolean isKeepTreeElementByHardReference(final PsiFileImpl $this) = true;
        
        @Hook(forceReturn = true)
        private static FileTrees withStub(final FileTrees $this, final StubTree stub, final @Nullable FileElement ast) throws StubTreeLoader.StubTreeAndIndexUnmatchCoarseException
                = (Privilege) ((Privilege) new FileTrees((Privilege) $this.myFile, new StrongReference<>(stub), (Privilege) $this.myTreeElementPointer, (Privilege) $this.myRefToPsi)).reconcilePsi(stub, ast, false);
        
        @Hook(forceReturn = true)
        private static ConcurrentMap<VirtualFile, FileViewProvider> getVFileToViewProviderMap(final FileManagerImpl $this) {
            final AtomicReference<ConcurrentMap<VirtualFile, FileViewProvider>> myVFileToViewProviderMap = (Privilege) $this.myVFileToViewProviderMap;
            ConcurrentMap<VirtualFile, FileViewProvider> map = myVFileToViewProviderMap.get();
            if (map == null)
                map = ConcurrencyUtil.cacheOrGet(myVFileToViewProviderMap, new ConcurrentHashMap<>());
            return map;
        }
        
        @Hook(forceReturn = true)
        private static ConcurrentMap<VirtualFile, PsiDirectory> getVFileToPsiDirMap(final FileManagerImpl $this) {
            final AtomicReference<ConcurrentMap<VirtualFile, PsiDirectory>> myVFileToPsiDirMap = (Privilege) $this.myVFileToPsiDirMap;
            ConcurrentMap<VirtualFile, PsiDirectory> map = myVFileToPsiDirMap.get();
            if (map == null)
                map = ConcurrencyUtil.cacheOrGet(myVFileToPsiDirMap, new ConcurrentHashMap<>());
            return map;
        }
        
        @Hook(exactMatch = false)
        private static void onClick(final @InvisibleType("com.intellij.openapi.wm.impl.status.MemoryUsagePanel$MemoryUsagePanelImpl$1") ClickListener $this) = Stream.of(ProjectManager.getInstance().getOpenProjects())
                .map(PsiManager::getInstance)
                .cast(PsiManagerEx.class)
                .map(PsiManagerEx::getFileManager)
                .cast(FileManagerImpl.class)
                .forEach(manager -> (Privilege) manager.processFileTypesChanged(true));
        
    }
    
    interface MemoryAPI extends StdCallLibrary, WinNT {
        
        MemoryAPI INSTANCE = Native.load("kernel32", MemoryAPI.class, W32APIOptions.DEFAULT_OPTIONS);
        
        int
                QUOTA_LIMITS_HARDWS_MIN_ENABLE  = 0x00000001,
                QUOTA_LIMITS_HARDWS_MIN_DISABLE = 0x00000002,
                QUOTA_LIMITS_HARDWS_MAX_ENABLE  = 0x00000004,
                QUOTA_LIMITS_HARDWS_MAX_DISABLE = 0x00000008;
        
        boolean SetProcessWorkingSetSizeEx(WinNT.HANDLE hProcess, long dwMinimumWorkingSetSize, long dwMaximumWorkingSetSize, int flags);
        
    }
    
    static void extendWorkingSetSize() {
        if (SystemInfo.isWindows) {
            final WinNT.HANDLE hProcess = Kernel32.INSTANCE.GetCurrentProcess();
            final long maxMemory = Runtime.getRuntime().maxMemory();
            final boolean result = MemoryAPI.INSTANCE.SetProcessWorkingSetSizeEx(hProcess, maxMemory * 2, -1, MemoryAPI.QUOTA_LIMITS_HARDWS_MIN_ENABLE | MemoryAPI.QUOTA_LIMITS_HARDWS_MAX_DISABLE);
            System.out.println(STR."SetProcessWorkingSetSizeEx: \{result}");
        }
    }
    
}
