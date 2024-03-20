package amadeus.maho.lang.idea.handler.base;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DiffLog;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.LazyParseableElement;
import com.intellij.psi.impl.source.tree.java.ClassElement;
import com.intellij.psi.impl.source.tree.java.JavaFileElement;
import com.intellij.psi.stubs.LightStubBuilder;
import com.intellij.util.indexing.FileBasedIndexImpl;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.idea.IDEAContext;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

import static amadeus.maho.util.bytecode.Bytecodes.ILOAD;

@TransformProvider
public interface ASTTransformer {
    
    Key<Boolean> transformedKey = { "transformed" };
    
    @Hook(value = DumbService.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static boolean isDumbAware(final boolean capture, final Object o) = capture && !(o instanceof HighlightingPass);
    
    static ASTNode transformASTNodes(final ASTNode astNode, final boolean loadingTreeElement) {
        if (astNode instanceof JavaFileElement) {
            for (ASTNode node = astNode.getFirstChildNode(); node != null; node = node.getTreeNext())
                if (node instanceof ClassElement targetNode)
                    Syntax.Marker.syntaxHandlers().values().forEach(handler -> handler.transformASTNode(targetNode, loadingTreeElement));
        } else if (astNode.getFirstChildNode() != null && astNode.getElementType().getLanguage() == JavaLanguage.INSTANCE)
            Syntax.Marker.syntaxHandlers().values().forEach(handler -> handler.transformASTNode(astNode, loadingTreeElement));
        return astNode;
    }
    
    ThreadLocal<AtomicInteger>
            collectGuard = ThreadLocal.withInitial(AtomicInteger::new),
            loadTreeGuard = ThreadLocal.withInitial(AtomicInteger::new);
    
    @Hook(exactMatch = false)
    private static void buildStubTree_$Enter(final LightStubBuilder $this) = collectGuard.get().getAndIncrement();
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)), exactMatch = false)
    private static void buildStubTree_$Exit(final LightStubBuilder $this) = collectGuard.get().getAndDecrement();
    
    @Hook(exactMatch = false)
    private static void indexFileContent_$Enter(final FileBasedIndexImpl $this) = collectGuard.get().getAndIncrement();
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)), exactMatch = false)
    private static void indexFileContent_$Exit(final FileBasedIndexImpl $this) = collectGuard.get().getAndDecrement();
    
    @Hook
    private static void loadTreeElement_$Enter(final PsiFileImpl $this) = loadTreeGuard.get().getAndIncrement();
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    private static void loadTreeElement_$Exit(final PsiFileImpl $this) = loadTreeGuard.get().getAndDecrement();
    
    @Hook
    private static void getStubbedSpine_$Enter(final FileElement $this) = loadTreeGuard.get().getAndIncrement();
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    private static void getStubbedSpine_$Exit(final FileElement $this) = loadTreeGuard.get().getAndDecrement();
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void ensureParsed(final LazyParseableElement $this) {
        if ($this.getElementType().getLanguage() == JavaLanguage.INSTANCE && loadTreeGuard.get().get() == 0)
            transform($this);
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void doActualPsiChange(final DiffLog $this, final PsiFile file) {
        if (file.getLanguage() == JavaLanguage.INSTANCE)
            ((Privilege) $this.myEntries).forEach(entry -> {
                switch (entry) {
                    case DiffLog.InsertEntry insert               -> transformASTNodes((Privilege) insert.myNewNode, false);
                    case DiffLog.ReplaceEntry replace             -> transformASTNodes((Privilege) replace.myNewChild, false);
                    case DiffLog.ReplaceElementWithEvents replace -> transformASTNodes((Privilege) replace.myNewRoot, false);
                    default                                       -> { }
                }
            });
    }
    
    @Hook(value = ResolveCache.class, isStatic = true, at = @At(var = @At.VarInsn(opcode = ILOAD, var = 2)), before = false, capture = true)
    private static <TRef, TResult> boolean resolve(final boolean capture, final TRef ref, final Map<TRef, TResult> cache, final boolean preventRecursion, final Computable<? extends TResult> resolver)
            = capture && !(ref instanceof PsiJavaCodeReferenceElementImpl);
    
    ThreadLocal<LinkedList<Object>> reentrant = ThreadLocal.withInitial(LinkedList::new);
    
    @SneakyThrows
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void calcTreeElement(final PsiFileImpl $this) = transform($this);
    
    private static void transform(final ElementBase element) {
        if (collectGuard.get().get() == 0 && element.getUserData(transformedKey) == null) {
            final LinkedList<Object> objects = reentrant.get();
            if (!objects.contains(element)) {
                objects << element;
                try {
                    IDEAContext.computeReadActionIgnoreDumbMode(() -> transformASTNodes(switch (element) {
                        case PsiElement psiElement -> psiElement.getNode();
                        case ASTNode astNode       -> astNode;
                        default                    -> throw new IllegalStateException(STR."Unexpected value: \{element}");
                    }, true));
                    element.putUserData(transformedKey, Boolean.TRUE);
                } finally { objects--; }
            }
        }
    }
    
}
