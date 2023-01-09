package amadeus.maho.lang.idea.debugger;

import java.util.List;

import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.tree.render.CompoundReferenceRenderer;
import com.intellij.debugger.ui.tree.render.ExpressionChildrenRenderer;
import com.intellij.debugger.ui.tree.render.LabelRenderer;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;

import amadeus.maho.lang.Privilege;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.DebugHelper;

import static com.intellij.debugger.settings.NodeRendererSettings.*;

@TransformProvider
public interface DebugRenderer {
    
    @Hook
    @Privilege
    private static void addAnnotationRenderers(final NodeRendererSettings $this, final List<NodeRenderer> renderers, final Project project) {
        try {
            visitAnnotatedElements(DebugHelper.Renderer.class.getCanonicalName(), project, (e, annotation) -> {
                if (e instanceof final PsiClass cls) {
                    final String expr = getAttributeValue(annotation, "value");
                    final LabelRenderer labelRenderer = StringUtil.isEmpty(expr) ? null : createLabelRenderer(null, expr);
                    final String childrenArray = getAttributeValue(annotation, "childrenArray");
                    final String isLeaf = getAttributeValue(annotation, "hasChildren");
                    final ExpressionChildrenRenderer childrenRenderer = StringUtil.isEmpty(childrenArray) ? null : createExpressionArrayChildrenRenderer(childrenArray, isLeaf, $this.myArrayRenderer);
                    final CompoundReferenceRenderer renderer = $this.createCompoundReferenceRenderer(cls.getQualifiedName(), cls.getQualifiedName(), labelRenderer, childrenRenderer);
                    renderer.setEnabled(true);
                    renderers.add(renderer);
                }
            });
        } catch (final IndexNotReadyException | ProcessCanceledException ignore) { } catch (final Exception e) { LOG.error(e); }
    }
    
}
