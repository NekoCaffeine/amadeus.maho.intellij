package amadeus.maho.lang.idea.handler;

import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface CommentHandler {
    
    @Hook(value = SuppressionUtil.class, isStatic = true)
    private static Hook.Result createComment(final Project project, @Hook.Reference String commentText, final Language language) {
        if (!commentText.startsWith(" ")) {
            commentText = " " + commentText;
            return { };
        }
        return Hook.Result.VOID;
    }
    
}
