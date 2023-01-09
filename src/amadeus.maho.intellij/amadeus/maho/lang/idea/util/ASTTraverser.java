package amadeus.maho.lang.idea.util;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.tree.LazyParseableElement;

import amadeus.maho.util.control.TreeIterator;

public interface ASTTraverser {
    
    static <N extends ASTNode> Stream<N> stream(final ASTNode root, final boolean skipLazyParseableElement, final Class<N> nodeType)
            = TreeIterator.ofRoot(ASTNode::getTreeParent, ASTNode::getTreeNext, skipLazyParseableElement ? node -> node != root && node instanceof LazyParseableElement ? null : node.getFirstChildNode() : ASTNode::getFirstChildNode, root)
            .dfs(true).filter(nodeType::isInstance).map(nodeType::cast);
    
    static <N extends ASTNode> void forEach(final ASTNode root, final boolean skipLazyParseableElement, final Class<N> nodeType, final Consumer<N> consumer) = stream(root, skipLazyParseableElement, nodeType).forEach(consumer);
    
    static <N extends ASTNode> void takeWhile(final ASTNode root, final boolean skipLazyParseableElement, final Class<N> nodeType, final Predicate<N> consumer) = stream(root, skipLazyParseableElement, nodeType).allMatch(consumer);
    
}
