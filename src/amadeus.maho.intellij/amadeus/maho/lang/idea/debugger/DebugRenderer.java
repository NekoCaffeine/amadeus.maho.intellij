package amadeus.maho.lang.idea.debugger;

import java.util.List;

import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.tree.render.CompoundReferenceRenderer;
import com.intellij.debugger.ui.tree.render.ExpressionChildrenRenderer;
import com.intellij.debugger.ui.tree.render.LabelRenderer;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.idea.debugger.render.CodePathPerceptionRenderer;
import amadeus.maho.lang.idea.debugger.render.LambdaRenderer;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Redirect;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.Slice;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.runtime.DebugHelper;

import static com.intellij.debugger.settings.NodeRendererSettings.*;

@TransformProvider
public interface DebugRenderer {
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static void getAllRenderers(final List<NodeRenderer> capture, final NodeRendererSettings $this, final Project project) {
        try {
            (Privilege) visitAnnotatedElements(DebugHelper.Renderer.class.getCanonicalName(), project, (e, annotation) -> {
                if (e instanceof PsiClass cls) {
                    final String expr = (Privilege) getAttributeValue(annotation, "value");
                    final @Nullable LabelRenderer labelRenderer = StringUtil.isEmpty(expr) ? null : (Privilege) createLabelRenderer(null, expr);
                    final String childrenArray = (Privilege) getAttributeValue(annotation, "childrenArray");
                    final String isLeaf = (Privilege) getAttributeValue(annotation, "hasChildren");
                    final @Nullable ExpressionChildrenRenderer childrenRenderer = StringUtil.isEmpty(childrenArray) ? null : (Privilege) createExpressionArrayChildrenRenderer(childrenArray, isLeaf, (Privilege) $this.myArrayRenderer);
                    final CompoundReferenceRenderer renderer = $this.createCompoundReferenceRenderer(cls.getQualifiedName(), cls.getQualifiedName(), labelRenderer, childrenRenderer);
                    renderer.setEnabled(true);
                    capture += renderer;
                }
            }, PsiClass.class);
            capture += new CodePathPerceptionRenderer().createRenderer();
            capture += new LambdaRenderer().createRenderer();
        } catch (final IndexNotReadyException | ProcessCanceledException ignore) { } catch (final Exception e) { ((Privilege) LOG).error(e); }
    }
    
    @Redirect(targetClass = CompoundReferenceRenderer.class, selector = ASMHelper._INIT_, slice = @Slice(@At(method = @At.MethodInsn(name = "assertTrue"))))
    private static boolean assertTrue(final Logger $this, final boolean value) = true;
    
}
