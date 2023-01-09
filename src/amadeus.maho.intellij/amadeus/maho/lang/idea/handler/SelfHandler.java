package amadeus.maho.lang.idea.handler;

import java.util.Optional;
import java.util.stream.Stream;

import com.intellij.codeInsight.completion.JavaCompletionSession;
import com.intellij.codeInsight.completion.JavaKeywordCompletion;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMethodUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.impl.source.PsiTypeElementImpl;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.ui.UIUtil;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.CallChain;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.idea.handler.base.BaseSyntaxHandler;
import amadeus.maho.lang.idea.handler.base.Syntax;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.function.FunctionHelper;

import static amadeus.maho.lang.idea.handler.SelfHandler.PRIORITY;

@TransformProvider
@Syntax(priority = PRIORITY)
public class SelfHandler extends BaseSyntaxHandler {
    
    public static final int PRIORITY = -1 << 2;
    
    public static final String self = "self";
    
    public static class SelfReferenceSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
        
        public SelfReferenceSearcher() = super(true);
        
        @Override
        public void processQuery(final ReferencesSearch.SearchParameters parameters, final Processor<? super PsiReference> processor) {
            final PsiElement target = parameters.getElementToSearch();
            if (target instanceof PsiClass && PsiSearchScopeUtil.isInScope(parameters.getEffectiveSearchScope(), target))
                PsiTreeUtil.findChildrenOfType(target, PsiJavaCodeReferenceElement.class).stream()
                        .filter(element -> element.getText().equals(self) && PsiTreeUtil.getContextOfType(element, PsiClass.class) == target)
                        .takeWhile(processor::process)
                        .forEach(FunctionHelper.abandon());
        }
        
    }
    
    @NoArgsConstructor
    public class ReplaceSelfWithInferredTypeAction extends LocalQuickFixAndIntentionActionOnPsiElement {
        
        @Override
        public String getText() = CommonQuickFixBundle.message("fix.replace.x.with.y", self, ((PsiTypeElement) getStartElement()).getType().getCanonicalText());
        
        @Override
        public String getFamilyName() = "Convert 'self' to the inferred type";
        
        @Override
        public boolean isAvailable(final Project project, final PsiFile file, final PsiElement startElement, final PsiElement endElement)
                = startElement instanceof PsiTypeElement && startElement.getText().equals(self) && inStaticContext(startElement);
        
        @Override
        public void invoke(final Project project, final PsiFile file, final @Nullable Editor editor, final PsiElement startElement, final PsiElement endElement)
                = startElement.replace(JavaPsiFacade.getElementFactory(project).createTypeElement(((PsiTypeElement) startElement).getType()));
        
    }
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class SelfLookupItem extends LookupElement implements TypedLookupItem {
        
        PsiClass context;
        
        @Override
        public Object getObject() = this;
        
        @Override
        public String getLookupString() = self;
        
        @Override
        public AutoCompletionPolicy getAutoCompletionPolicy() = AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE;
        
        @Override
        public boolean equals(final Object o) = o instanceof SelfLookupItem;
        
        @Override
        public int hashCode() = getLookupString().hashCode();
        
        @Override
        public void renderElement(final LookupElementPresentation presentation) {
            presentation.setItemText("self " + UIUtil.rightArrow() + " " + new PsiImmediateClassType(context, PsiSubstitutor.EMPTY).getPresentableText());
            presentation.setItemTextBold(true);
        }
        
        @Override
        public PsiType getType() = new PsiImmediateClassType(context, PsiSubstitutor.EMPTY);
        
    }
    
    public static boolean isSelfReference(final @Nullable String type) = self.equals(type);
    
    public static boolean isSelfReference(final @Nullable PsiJavaCodeReferenceElement reference) = reference != null && ReadAction.compute(() -> {
        if (isSelfReference(reference.getText())) {
            final @Nullable PsiElement owner = PsiTreeUtil.getContextOfType(reference, PsiTypeParameterListOwner.class);
            return !(owner instanceof PsiClass);
        }
        return false;
    });
    
    public static boolean isSelfReturn(final PsiReturnStatement statement) = isSelfReference(PsiTreeUtil.getParentOfType(statement, PsiMethod.class)?.getReturnTypeElement()?.getText() ?? null);
    
    @Hook
    private static Hook.Result getType(final PsiMethodCallExpressionImpl $this) {
        final @Nullable PsiMethod method = $this.resolveMethod();
        if (method != null && (method.getReturnTypeElement() != null && method.getReturnTypeElement().getText().equals(self) || method.hasAnnotation(CallChain.class.getCanonicalName())))
            return Hook.Result.nullToVoid($this.getMethodExpression().getQualifierExpression()?.getType() ?? resolveThis($this));
        return Hook.Result.VOID;
    }
    
    private static @Nullable PsiType resolveThis(final PsiMethodCallExpression expression) {
        for (PsiElement scope = expression.getContext(); scope != null; scope = scope.getContext())
            switch (scope) {
                case PsiExpressionList list && list.getParent() instanceof PsiAnonymousClass -> scope = scope.getParent();
                case PsiClass psiClass                                                       -> {
                    return new PsiImmediateClassType(psiClass, PsiSubstitutor.EMPTY);
                }
                case JavaCodeFragment fragment                                               -> {
                    final @Nullable PsiType fragmentThisType = fragment.getThisType();
                    if (fragmentThisType != null)
                        return fragmentThisType;
                }
                default                                                                      -> { }
            }
        return null;
    }
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkVarTypeApplicability(final PsiTypeElement typeElement) = Hook.Result.falseToVoid(isSelfReference(typeElement.getInnermostComponentReferenceElement()), null);
    
    @Hook(value = HighlightControlFlowUtil.class, isStatic = true)
    private static Hook.Result checkMissingReturnStatement(final @Nullable PsiCodeBlock body, final @Nullable PsiType returnType) {
        if (body != null && body.getParent() instanceof PsiMethod method) {
            final @Nullable PsiTypeElement returnTypeElement = method.getReturnTypeElement();
            if (returnTypeElement != null && isSelfReference(returnTypeElement.getText()))
                return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook(value = HighlightMethodUtil.class, isStatic = true)
    private static Hook.Result createIncompatibleTypeHighlightInfo(final PsiCall methodCall, final PsiResolveHelper resolveHelper, final MethodCandidateInfo resolveResult, final PsiElement elementToHighlight)
            = Hook.Result.falseToVoid(methodCall.getParent() instanceof PsiReturnStatement statement && statement.getParent().getParent() instanceof PsiMethod method && isSelfReference(returnTypeText(method)), null);
    
    private static @Nullable String returnTypeText(final PsiMethod method) {
        final @Nullable PsiTypeElement element = method.getReturnTypeElement();
        return element == null ? null : element.getText();
    }
    
    @Hook(value = OverrideImplementUtil.class, isStatic = true)
    public static void decorateMethod(final PsiClass aClass, final PsiMethod method, final boolean toCopyJavaDoc, final boolean insertOverrideIfPossible, final PsiMethod result) {
        if (isSelfReference(returnTypeText(method)) && result.getReturnTypeElement() instanceof PsiTypeElementImpl typeElement)
            typeElement.replace(JavaPsiFacade.getElementFactory(aClass.getProject()).createTypeElementFromText(self, result));
    }
    
    @Hook(value = JavaKeywordCompletion.class, isStatic = true, at = @At(field = @At.FieldInsn(name = "PRIMITIVE_TYPES"), ordinal = 1))
    private static void addPrimitiveTypes(final Consumer<? super LookupElement> result, final PsiElement position, final JavaCompletionSession session)
            = Optional.ofNullable(PsiTreeUtil.getContextOfType(position, PsiClassImpl.class)).map(SelfLookupItem::new).ifPresent(result::consume);
    
    @Hook
    private static Hook.Result getTypeParameters(final PsiJavaCodeReferenceElementImpl $this) {
        if (isSelfReference($this)) {
            final @Nullable PsiClass scope = PsiTreeUtil.getContextOfType($this, PsiClassImpl.class);
            if (scope != null)
                return { Stream.of(scope.getTypeParameters()).map(PsiSubstitutor.EMPTY::substitute).toArray(PsiType.ARRAY_FACTORY::create) };
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result multiResolve(final PsiJavaCodeReferenceElementImpl $this, final boolean incompleteCode) {
        if (isSelfReference($this)) {
            final @Nullable PsiClass scope = PsiTreeUtil.getContextOfType($this, PsiClassImpl.class);
            if (scope != null) {
                final PsiClassType.ClassResolveResult result = new PsiImmediateClassType(scope, PsiSubstitutor.EMPTY).resolveGenerics();
                final @Nullable PsiClass element = result.getElement();
                if (element != null)
                    return { new JavaResolveResult[]{ new CandidateInfo(element, result.getSubstitutor(), $this, false) } };
            }
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result isReferenceTo(final PsiJavaCodeReferenceElementImpl $this, final PsiElement element) {
        if (isSelfReference($this)) {
            final @Nullable PsiClass scope = PsiTreeUtil.getContextOfType($this, PsiClassImpl.class);
            if (scope != null)
                return { scope.isEquivalentTo(element) };
        }
        return Hook.Result.VOID;
    }
    
    @Override
    public void inferType(final PsiTypeElement tree, final PsiType result[]) = DumbService.getInstance(tree.getProject()).runReadActionInSmartMode(() -> {
        if (tree.getText().equals(self)) {
            final @Nullable PsiClass scope = PsiTreeUtil.getContextOfType(tree, PsiClassImpl.class);
            if (scope != null)
                result[0] = new PsiImmediateClassType(scope, PsiSubstitutor.EMPTY);
        }
    });
    
    @Override
    public void check(final PsiElement tree, final ProblemsHolder holder, final QuickFixFactory quickFix, final boolean isOnTheFly) {
        if (tree instanceof PsiJavaCodeReferenceElement referenceElement && isSelfReference(referenceElement) && referenceElement.resolve() instanceof PsiClass && inStaticContext(tree))
            registerProblem(tree, holder);
    }
    
    private boolean inStaticContext(final PsiElement tree) {
        final @Nullable PsiMember context = PsiTreeUtil.getContextOfType(tree, PsiClassImpl.class, PsiField.class, PsiMethod.class, PsiClassInitializer.class);
        if (context != null && !(context instanceof PsiClass))
            return context.hasModifierProperty(PsiModifier.STATIC);
        return false;
    }
    
    private void registerProblem(final PsiElement tree, final ProblemsHolder holder)
            = holder.registerProblem(tree, "'self' cannot be used in a static context", ProblemHighlightType.GENERIC_ERROR, new ReplaceSelfWithInferredTypeAction(tree));
    
}
