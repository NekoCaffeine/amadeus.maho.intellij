package amadeus.maho.lang.idea.handler.base;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.impl.source.JavaFileElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;

import amadeus.maho.lang.idea.IDEAContext;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

public class JavaExpressionIndex extends FileBasedIndexExtension<String, int[]> {
    
    public record IndexType<E extends PsiExpression>(String name, Class<E> expressionType, Predicate<E> predicate = _ -> true) {
        
        public IndexType { targets.computeIfAbsent(expressionType, _ -> new CopyOnWriteArrayList<>()) += this; }
        
    }
    
    public static final ID<String, int[]> INDEX_ID = ID.create("java.expression");
    
    private static final ConcurrentWeakIdentityHashMap<Class<?>, List<IndexType<?>>> targets = { };
    
    static { Handler.Marker.baseHandlers(); }
    
    @Override
    public ID<String, int[]> getName() = INDEX_ID;
    
    @Override
    public DataIndexer<String, int[], FileContent> getIndexer() = inputData -> {
        final PsiFile file = inputData.getPsiFile();
        if (IDEAContext.requiresMaho(file)) {
            final HashMap<String, IntList> mapping = { };
            SyntaxTraverser.psiTraverser(file)
                    .filter(PsiExpression.class)
                    .forEach(expression -> {
                        if (expression instanceof TreeElement element) {
                            final @Nullable List<IndexType<? extends PsiExpression>> indexTypes = targets[expression.getClass()];
                            if (indexTypes != null)
                                indexTypes.forEach(indexType -> {
                                    if (((Predicate<PsiExpression>) indexType.predicate()).test(expression))
                                        mapping.computeIfAbsent(indexType.name(), _ -> new IntArrayList()).add(element.getStartOffset());
                                });
                        }
                    });
            return mapping.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toIntArray()));
        }
        return Map.of();
    };
    
    @Override
    public KeyDescriptor<String> getKeyDescriptor() = EnumeratorStringDescriptor.INSTANCE;
    
    @Override
    public DataExternalizer<int[]> getValueExternalizer() = new DataExternalizer<>() {
        
        @Override
        public void save(final DataOutput out, final int[] values) throws IOException {
            DataInputOutputUtil.writeINT(out, values.length);
            for (final int i : values)
                out.writeInt(i);
        }
        
        @Override
        public int[] read(final DataInput in) throws IOException {
            final int size = DataInputOutputUtil.readINT(in), values[] = new int[size];
            for (int i = 0; i < size; i++)
                values[i] = in.readInt();
            return values;
        }
        
    };
    
    @Override
    public int getVersion() = 1;
    
    @Override
    public boolean hasSnapshotMapping() = true;
    
    @Override
    public boolean needsForwardIndexWhenSharing() = false;
    
    @Override
    public FileBasedIndex.InputFilter getInputFilter() = new DefaultFileTypeSpecificInputFilter(JavaFileType.INSTANCE) {
        
        @Override
        public boolean acceptInput(final VirtualFile file) = super.acceptInput(file) && JavaFileElementType.isInSourceContent(file);
        
    };
    
    @Override
    public boolean dependsOnFileContent() = true;
    
}
