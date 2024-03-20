package amadeus.maho.lang.idea.handler;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JPanel;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.ExternalAnnotationsManagerImpl;
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.hints.presentation.VerticalListInlayPresentation;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;

import amadeus.maho.lang.idea.light.LightNameValuePair;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class ExternalAnnotationsHandler {
    
    public static final String Comment = "Comment", TITLE = "Insert external comments";
    
    public static class MakeCommentExternal extends BaseIntentionAction {
        
        @Override
        public String getFamilyName() = "MakeCommentExternal";
        
        @Override
        public boolean isAvailable(final Project project, final Editor editor, final PsiFile file) {
            final PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PsiModifierListOwner.class);
            if (owner != null && ExternalAnnotationsManagerImpl.areExternalAnnotationsApplicable(owner)) {
                setText("Comment external");
                return true;
            }
            return false;
        }
        
        @Override
        public void invoke(final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
            final @Nullable PsiModifierListOwner owner = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PsiModifierListOwner.class);
            if (owner != null) {
                final ExternalAnnotationsManager manager = ExternalAnnotationsManager.getInstance(project);
                final @Nullable PsiAnnotation comment = manager.findExternalAnnotation(owner, Comment);
                final JBTextArea area = { 20, 120 };
                if (comment != null) {
                    final @Nullable PsiAnnotationMemberValue value = comment.findAttributeValue(null);
                    if (value != null) {
                        final String text = value.getText();
                        if (text.length() > 2)
                            area.setText(text.substring(1, text.length() - 1).replace("_\\n_", "\n"));
                    }
                }
                if (createDialog(project, area).showAndGet()) {
                    final String result = area.getText();
                    if (result.isEmpty())
                        manager.deannotate(owner, Comment);
                    else
                        try {
                            manager.annotateExternally(owner, Comment, file, new PsiNameValuePair[]{ new LightNameValuePair(owner.getManager(), null, '"' + result.replace("\n", "_\\n_") + '"') });
                        } catch (final ExternalAnnotationsManager.CanceledConfigurationException ignored) { }
                }
            }
        }
        
        private static DialogBuilder createDialog(final Project project, final JBTextArea field) {
            final JPanel panel = { new GridBagLayout() };
            final GridBag bag = new GridBag().setDefaultAnchor(GridBagConstraints.WEST).setDefaultFill(GridBagConstraints.HORIZONTAL)
                    .setDefaultInsets(JBUI.insets(2)).setDefaultWeightX(1.0).setDefaultWeightY(1.0);
            panel.add(field, bag.next());
            final DialogBuilder builder = new DialogBuilder(project).setNorthPanel(panel).title(TITLE);
            builder.setPreferredFocusComponent(field);
            builder.setHelpId("insert_external_comments");
            return builder;
        }
        
        @Override
        public boolean startInWriteAction() = false;
        
    }
    
    @Hook
    private static Hook.Result annotationPresentation(final @InvisibleType("com.intellij.codeInsight.hints.AnnotationInlayProvider$getCollectorFor$1") FactoryInlayHintsCollector $this,
            final PsiAnnotation annotation) = annotation.getQualifiedName()?.equals(Comment) ?? false ? new Hook.Result(presentation($this.getFactory(), annotation.findAttributeValue(null)?.getText() ?? "")) : Hook.Result.VOID;
    
    public static InlayPresentation presentation(final PresentationFactory factory, final String comment) {
        if (comment.length() < 2)
            return factory.smallText("");
        final String text = comment.substring(1, comment.length() - 1), comments[] = text.split("_\\\\n_");
        return comments.length == 1 ? factory.text(STR."// \{text}") :
                new VerticalListInlayPresentation(Stream.concat(Stream.concat(Stream.of("/*"), Stream.of(comments).map("    "::concat)), Stream.of("*/")).map(factory::text).collect(Collectors.toList()));
    }
    
}
