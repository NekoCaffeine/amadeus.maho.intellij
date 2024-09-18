package amadeus.maho.lang.idea.llm;

import java.util.List;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CommitMessageI;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.ObjectUtils;
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.llm.LLM;

import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

public class CommitMessageGenerateAction extends AnAction implements DumbAware {
    
    @Override
    public void actionPerformed(final AnActionEvent e) {
        final @Nullable Project project = e.getProject();
        if (project == null)
            return;
        final @Nullable AbstractCommitWorkflowHandler<?, ?> workflowHandler = ObjectUtils.tryCast(e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER), AbstractCommitWorkflowHandler.class);
        if (workflowHandler == null)
            return;
        final @Nullable CommitMessageI commitMessageI = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL);
        if (commitMessageI == null)
            return;
        final GitRepositoryManager gitRepositoryManager = GitRepositoryManager.getInstance(project);
        final List<Change> changes = workflowHandler.getUi().getIncludedChanges();
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating commit message") {
            
            @Override
            public void run(@NotNull final ProgressIndicator indicator) {
                commitMessageI.setCommitMessage("Generating commit message...");
                try {
                    final String diff = workflowHandler.getDiff();
                    final String commitMessage = generateCommitMessage(diff);
                    commitMessageI.setCommitMessage(commitMessage);
                } catch (final Throwable t) {
                    commitMessageI.setCommitMessage(STR."Failed to generate commit message\n  \{t.getClass().getName()}: \{t.getMessage()}");
                }
            }
            
        });
    }
    
    private static
    
    @LLM
    public static String generateCommitMessage(String diff);
    
}
