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
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiUnaryExpression;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.impl.source.JavaFileElementType;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.java.PsiArrayAccessExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiArrayInitializerExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiAssignmentExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiBinaryExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiPolyadicExpressionImpl;
import com.intellij.psi.util.PsiTreeUtil;
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

import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.idea.IDEAContext;
import amadeus.maho.lang.idea.handler.OperatorOverloadingHandler;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import static amadeus.maho.lang.idea.IDEAContext.OperatorData.*;

public class JavaExpressionIndex extends FileBasedIndexExtension<String, JavaExpressionIndex.Offsets> {
    
    @ToString
    @EqualsAndHashCode
    public record Offsets(int array[]) { }
    
    public record IndexType<E extends PsiExpression>(String name, Class<E> expressionType, Predicate<E> predicate = _ -> true) {
        
        public IndexType { IndexTypes.indexTypes.computeIfAbsent(expressionType, _ -> new CopyOnWriteArrayList<>()) += this; }
        
        public static @Nullable PsiJavaToken token(final PsiExpression expression) = switch (expression) {
            case PsiUnaryExpression unaryExpression                               -> unaryExpression.getOperationSign();
            case PsiBinaryExpressionImpl binaryExpression                         -> binaryExpression.getOperationSign();
            case PsiPolyadicExpressionImpl polyadicExpression                     -> polyadicExpression.findChildByRoleAsPsiElement(ChildRole.OPERATION_SIGN) instanceof PsiJavaToken token ? token : null;
            case PsiAssignmentExpressionImpl assignmentExpression                 -> assignmentExpression.getOperationSign();
            case PsiArrayAccessExpressionImpl accessExpression                    -> accessExpression.findChildByRoleAsPsiElement(ChildRole.LBRACKET) instanceof PsiJavaToken token ? token : null;
            case PsiArrayInitializerExpressionImpl arrayInitializerExpression     -> arrayInitializerExpression.findChildByRoleAsPsiElement(ChildRole.LBRACE) instanceof PsiJavaToken token ? token : null;
            // case AssignHandler.PsiArrayInitializerBackNewExpression newExpression -> newExpression.getArgumentList().getFirstChild() instanceof PsiJavaToken token ? token : null;
            case null,
                 default                                                          -> null;
        };
        
    }
    
    public interface IndexTypes {
        
        ConcurrentWeakIdentityHashMap<Class<?>, List<IndexType<?>>> indexTypes = { };
        
        JavaExpressionIndex.IndexType<PsiArrayInitializerExpressionImpl> ASSIGN_NEW = { "assign-new", PsiArrayInitializerExpressionImpl.class };
        
        // { indexTypes.computeIfAbsent(PsiArrayInitializerExpressionImpl.class, _ -> new CopyOnWriteArrayList<>()) += ASSIGN_NEW; } // before transform ast
        
        Map<String, List<JavaExpressionIndex.IndexType>> operatorTypes = operatorName2operatorType.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> operatorName2expressionTypes[entry.getKey()].stream().map(expressionType -> new JavaExpressionIndex.IndexType(entry.getKey(), expressionType, expression -> {
                    final @Nullable PsiJavaToken token = PsiTreeUtil.getChildOfType((PsiExpression) expression, PsiJavaToken.class);
                    return token != null && token.getTokenType() == entry.getValue();
                })).toList()));
        
        JavaExpressionIndex.IndexType<PsiArrayAccessExpressionImpl> GET = { "GET", PsiArrayAccessExpressionImpl.class };
        
        JavaExpressionIndex.IndexType<PsiAssignmentExpressionImpl> PUT = { "PUT", PsiAssignmentExpressionImpl.class, assignmentExpression -> assignmentExpression.getLExpression() instanceof PsiArrayAccessExpression };
        
    }
    
    public static final ID<String, Offsets> INDEX_ID = ID.create("java.expression");
    
    @Override
    public ID<String, Offsets> getName() = INDEX_ID;
    
    @Override
    public DataIndexer<String, Offsets, FileContent> getIndexer() = inputData -> {
        final PsiFile file = inputData.getPsiFile();
        if (IDEAContext.requiresMaho(file)) {
            final HashMap<String, IntList> mapping = { };
            SyntaxTraverser.psiTraverser(file)
                    .filter(PsiExpression.class)
                    .forEach(expression -> {
                        if (IndexType.token(expression) instanceof PsiJavaToken token && !OperatorOverloadingHandler.cannotOverload(token.getTokenType()))
                            IndexTypes.indexTypes[expression.getClass()]?.forEach(indexType -> {
                                if (((Predicate<PsiExpression>) indexType.predicate()).test(expression))
                                    mapping.computeIfAbsent(indexType.name(), _ -> new IntArrayList()).add(token.getNode().getStartOffset());
                            });
                    });
            return mapping.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> new Offsets(entry.getValue().toIntArray())));
        }
        return Map.of();
    };
    
    @Override
    public KeyDescriptor<String> getKeyDescriptor() = EnumeratorStringDescriptor.INSTANCE;
    
    @Override
    public DataExternalizer<Offsets> getValueExternalizer() = new DataExternalizer<>() {
        
        @Override
        public void save(final DataOutput out, final Offsets offsets) throws IOException {
            final int values[] = offsets.array;
            DataInputOutputUtil.writeINT(out, values.length);
            for (final int i : values)
                out.writeInt(i);
        }
        
        @Override
        public Offsets read(final DataInput in) throws IOException {
            final int size = DataInputOutputUtil.readINT(in), values[] = new int[size];
            for (int i = 0; i < size; i++)
                values[i] = in.readInt();
            return { values };
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
