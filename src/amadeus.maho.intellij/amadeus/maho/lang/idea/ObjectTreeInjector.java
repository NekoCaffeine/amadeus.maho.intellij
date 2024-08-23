package amadeus.maho.lang.idea;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jdk.internal.misc.Unsafe;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ObjectTree;

import amadeus.maho.lang.Privilege;
import amadeus.maho.util.concurrent.ConcurrentIdentityHashMap;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;
import amadeus.maho.util.dynamic.ReferenceCollector;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.runtime.UnsafeHelper;

public interface ObjectTreeInjector { // FWIW ObjectTree performance increase was 1000x. O(2^N) => O(1)
    
    ReferenceCollector.Base collector = new ReferenceCollector.Base().let(ReferenceCollector.Base::start);
    
    static void inject() {
        final ObjectTree tree = (Privilege) Disposer.ourTree;
        try {
            final Field
                    myObject2ParentNodeField = tree.getClass().getDeclaredField("myObject2ParentNode"),
                    myDisposedObjectsField = tree.getClass().getDeclaredField("myDisposedObjects");
            final Unsafe unsafe = UnsafeHelper.unsafe();
            final long
                    myObject2ParentNodeFieldOffset = unsafe.objectFieldOffset(myObject2ParentNodeField),
                    myDisposedObjectsOffset = unsafe.objectFieldOffset(myDisposedObjectsField);
            synchronized ((Privilege) tree.getTreeLock()) {
                {
                    final Map<Disposable, Object> myObject2ParentNode = (Map<Disposable, Object>) unsafe.getReference(tree, myObject2ParentNodeFieldOffset);
                    final ConcurrentIdentityHashMap<Disposable, Object> concurrentHashMap = { 1 << 10, 0.5F };
                    concurrentHashMap.putAll(myObject2ParentNode);
                    unsafe.putReferenceVolatile(tree, myObject2ParentNodeFieldOffset, concurrentHashMap);
                }
                {
                    final Map<Disposable, Object> myDisposedObjects = (Map<Disposable, Object>) unsafe.getReference(tree, myDisposedObjectsOffset);
                    final ConcurrentHashMap<ConcurrentWeakIdentityHashMap.Key<Disposable>, Object> concurrentHashMap = { 1 << 16, 0.5F };
                    final ConcurrentWeakIdentityHashMap.Managed<Disposable, Object> concurrentWeakIdentityHashMap = { concurrentHashMap };
                    concurrentWeakIdentityHashMap.putAll(myDisposedObjects);
                    collector.manage(concurrentWeakIdentityHashMap);
                    unsafe.putReferenceVolatile(tree, myDisposedObjectsOffset, concurrentWeakIdentityHashMap);
                }
            }
        } catch (final NoSuchFieldException ignored) { DebugHelper.breakpoint(); }
    }
    
}
