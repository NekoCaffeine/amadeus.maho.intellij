package amadeus.maho.lang.idea;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jdk.internal.misc.Unsafe;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ObjectTree;

import amadeus.maho.lang.Privilege;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.runtime.UnsafeHelper;

public class ObjectTreeInjector { // FWIW ObjectTree performance increase was 1000x. O(2^N) => O(1)
    
    public static void inject() {
        final ObjectTree tree = Disposer.getTree();
        try {
            final Field disposedObjects = tree.getClass().getDeclaredField("myDisposedObjects");
            final Unsafe unsafe = UnsafeHelper.unsafe();
            final long objectFieldOffset = unsafe.objectFieldOffset(disposedObjects);
            synchronized ((Privilege) tree.getTreeLock()) {
                final Map<Disposable, Object> myDisposedObjects = (Map<Disposable, Object>) unsafe.getReference(tree, objectFieldOffset);
                final ConcurrentHashMap<ConcurrentWeakIdentityHashMap.Key<Disposable>, Object> concurrentHashMap = { 1 << 20, 0.5F };
                final ConcurrentWeakIdentityHashMap.Managed<Disposable, Object> concurrentWeakIdentityHashMap = { concurrentHashMap };
                concurrentWeakIdentityHashMap.putAll(myDisposedObjects);
                LargeMemoryPatcher.collector.manage(concurrentWeakIdentityHashMap);
                unsafe.putReferenceVolatile(tree, objectFieldOffset, concurrentWeakIdentityHashMap);
            }
        } catch (final NoSuchFieldException ignored) { DebugHelper.breakpoint(); }
    }
    
}
