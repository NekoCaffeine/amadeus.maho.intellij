package amadeus.maho.lang.idea.handler;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiSwitchExpression;
import com.intellij.psi.PsiSwitchLabeledRuleStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.java.AbstractJavaBlock;
import com.intellij.psi.formatter.java.JavaSpacePropertyProcessor;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.util.PsiTreeUtil;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

import com.siyeh.ig.style.UnnecessaryParenthesesInspection;

import static amadeus.maho.lang.idea.IDEAContext.*;
import static com.intellij.psi.JavaTokenType.ARROW;

@TransformProvider
public class SwitchHandler {
    
    @Hook(value = HighlightUtil.class, isStatic = true, at = @At(method = @At.MethodInsn(name = "equals")), before = false, capture = true, branchReversal = true)
    private static boolean checkSwitchExpressionReturnTypeCompatible_$VoidCheck(final boolean capture, final PsiSwitchExpression expression, final HighlightInfoHolder holder)
            = capture || expression.getParent() instanceof PsiExpressionStatement ||
              expression.getParent() instanceof PsiReturnStatement returnStatement && PsiType.VOID.equals(PsiTreeUtil.getParentOfType(returnStatement, PsiMethod.class)?.getReturnType() ?? null);
    
    @Hook(value = HighlightUtil.class, isStatic = true, at = @At(method = @At.MethodInsn(name = "areTypesAssignmentCompatible")), before = false, capture = true, branchReversal = true)
    private static boolean checkSwitchExpressionReturnTypeCompatible_$CompatibleCheck(final boolean capture, final PsiSwitchExpression expression, final HighlightInfoHolder holder)
            = capture || PsiType.VOID.equals(expression.getType());
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkSwitchExpressionHasResult(final PsiSwitchExpression expression, final HighlightInfoHolder holder)
            = Hook.Result.falseToVoid(PsiType.VOID.equals(expression.getType()) || expression.getParent() instanceof PsiReturnStatement statement && SelfHandler.isSelfReturn(statement));
    
    @Hook
    private static Hook.Result visitParenthesizedExpression(final UnnecessaryParenthesesInspection.UnnecessaryParenthesesVisitor $this, final PsiParenthesizedExpression expression)
            = Hook.Result.falseToVoid(expression.getExpression() instanceof PsiSwitchExpression && expression.getParent() instanceof PsiBinaryExpression binaryExpression && binaryExpression.getLOperand() == expression, null);
    
    /*
        before:
            switch (...) {
                case A,
                        B,
                        C ->
            }
        
        after:
            switch (...) {
                case A,
                     B,
                     C ->
            }
    */
    @Hook
    private static Hook.Result createChildAlignment(final AbstractJavaBlock $this) {
        final ASTNode node = $this.getNode();
        if (node.getElementType() == JavaElementType.EXPRESSION_LIST) {
            final @Nullable ASTNode parent = node.getTreeParent();
            if (parent != null && parent.getElementType() == JavaElementType.SWITCH_LABELED_RULE)
                return { AbstractJavaBlock.createAlignment(true, null) };
        }
        return Hook.Result.VOID;
    }
    
    /*
        before:
            switch (...) {
                case A, B, C ->
            }
    
        after:
            switch (...) {
                case A,
                     B,
                     C ->
            }
    */
    @Hook
    private static Hook.Result visitExpressionList(final JavaSpacePropertyProcessor $this, final PsiExpressionList list) {
        if (list.getParent() instanceof PsiSwitchLabeledRuleStatement && myRole1($this) == ChildRole.COMMA) {
            final CommonCodeStyleSettings mySettings = mySettings($this);
            myResult($this, Spacing.createSpacing(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE));
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    /*
        before:
            switch (...) {
                case A ->
                     BB ->
                     CCC ->
            }
    
        after:
            switch (...) {
                case A   ->
                     BB  ->
                     CCC ->
            }
    */
    @Hook
    private static Hook.Result visitSwitchLabeledRuleStatement(final JavaSpacePropertyProcessor $this, final PsiSwitchLabeledRuleStatement statement) {
        if (myType2($this) == ARROW) {
            final PsiElement arrow = myChild2($this).getPsi();
            @Nullable PsiElement parent = statement.getParent();
            if (parent != null) {
                parent = parent.getParent();
                if (parent instanceof PsiSwitchBlock switchBlock) {
                    final int space = maxOffset(switchBlock) - lineOffset(arrow.getPrevSibling(), switchBlock) + 1;
                    final CommonCodeStyleSettings mySettings = mySettings($this);
                    myResult($this, Spacing.createSpacing(space, space, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE));
                }
            }
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    protected static int maxOffset(final PsiSwitchBlock block) = PsiTreeUtil.getChildrenOfTypeAsList(block.getBody(), PsiSwitchLabeledRuleStatement.class).stream()
            .map(statement -> PsiTreeUtil.findSiblingForward(statement.getFirstChild(), ARROW, null))
            .nonnull()
            .mapToInt(arrow -> lineOffset(arrow.getPrevSibling(), block))
            .max()
            .orElse(0);
    
    protected static int lineOffset(final PsiElement element, final PsiSwitchBlock holder) {
        if (element instanceof PsiWhiteSpace)
            return lineOffset(element.getPrevSibling(), holder);
        int offset = 0;
        PsiElement context = element;
        do
            offset += context.getStartOffsetInParent();
        while ((context = context.getParent()) != holder);
        offset += element.getTextLength();
        return offset - StringUtil.lastIndexOf(holder.getText(), '\n', 0, offset);
    }
    
}
