package amadeus.maho.lang.idea.handler.base;

import java.util.Set;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;

import amadeus.maho.lang.idea.IDEAContext;

public class BaseSyntaxHandler extends IDEAContext {
    
    public void transformASTNode(final ASTNode root, final boolean loadingTreeElement) { }
    
    public boolean contextFilter(final PsiClass context) = !(context instanceof PsiCompiledElement);
    
    public void process(final PsiElement tree, final ExtensibleMembers members, final PsiClass context) {
        if (contextFilter(context))
            switch (tree) {
                case PsiField element           -> processVariable(element, members, context);
                case PsiMethod element          -> processMethod(element, members, context);
                case PsiClass element           -> processClass(element, members, context);
                case PsiRecordComponent element -> processRecordComponent(element, members, context);
                default                         -> throw new AssertionError(STR."Unreachable area: \{tree.getClass()}");
            }
    }
    
    public void processVariable(final PsiField tree, final ExtensibleMembers members, final PsiClass context) { }
    
    public void processMethod(final PsiMethod tree, final ExtensibleMembers members, final PsiClass context) { }
    
    public void processClass(final PsiClass tree, final ExtensibleMembers members, final PsiClass context) { }
    
    public void processRecordComponent(final PsiRecordComponent tree, final ExtensibleMembers members, final PsiClass context) { }
    
    public void collectRelatedTarget(final PsiModifierListOwner tree, final Set<PsiNameIdentifierOwner> targets) { }
    
    public void inferType(final PsiTypeElement tree, final PsiType result[]) { }
    
    public void check(final PsiElement tree, final ProblemsHolder holder, final QuickFixFactory quickFix, final boolean isOnTheFly) { }
    
    public boolean isVariableOut(final PsiVariable variable) = false;
    
    public boolean isImplicitUsage(final PsiElement tree, final ImplicitUsageChecker.RefData refData) = false;
    
    public boolean isImplicitRead(final PsiElement tree, final ImplicitUsageChecker.RefData refData) = false;
    
    public boolean isImplicitWrite(final PsiElement tree, final ImplicitUsageChecker.RefData refData) = false;
    
}
