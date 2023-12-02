package amadeus.maho.lang.idea.handler.base;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.spellchecker.JavaSpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.idea.light.LightElement;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Redirect;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.Slice;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.bytecode.ASMHelper;

import static amadeus.maho.lang.idea.IDEAContext.requiresMaho;
import static amadeus.maho.util.bytecode.Bytecodes.ATHROW;

@TransformProvider
public class InspectionTool implements InspectionToolProvider {
    
    public static class Handler extends AbstractBaseJavaLocalInspectionTool {
        
        @Getter
        @RequiredArgsConstructor
        @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
        public static class Checker extends JavaElementVisitor {
            
            ProblemsHolder holder;
            
            boolean isOnTheFly;
            
            QuickFixFactory quickFix = QuickFixFactory.getInstance();
            
            @Override
            public void visitReferenceExpression(final PsiReferenceExpression expression) = visitElement(expression);
            
            @Override
            public void visitElement(final PsiElement element) {
                super.visitElement(element);
                if (requiresMaho(element)) {
                    Syntax.Marker.syntaxHandlers().values().forEach(handler -> handler.check(element, holder, quickFix, isOnTheFly));
                    if (element instanceof PsiModifierListOwner owner)
                        HandlerSupport.process(owner, (handler, target, annotation, annotationTree) -> handler.check(element, annotation, annotationTree, holder, quickFix));
                }
            }
            
        }
        
        @Override
        public String getDisplayName() = "Maho annotations inspection";
        
        @Override
        public String getShortName() = "MahoAnnotationsInspection";
        
        @Override
        public HighlightDisplayLevel getDefaultLevel() = HighlightDisplayLevel.ERROR;
        
        @Override
        public String[] getGroupPath() = { "Maho" };
        
        @Override
        public String getGroupDisplayName() = InspectionsBundle.message("group.names.compiler.issues");
        
        @Override
        public boolean isEnabledByDefault() = true;
        
        @Override
        public Handler.Checker buildVisitor(final ProblemsHolder holder, final boolean isOnTheFly) = { holder, isOnTheFly };
        
    }
    
    // The following code should probably be removed in the future
    
    @Redirect(target = "com.intellij.codeInspection.dataFlow.DataFlowInstructionVisitor", selector = "beforeExpressionPush", slice = @Slice(@At(insn = @At.Insn(opcode = ATHROW))))
    private static void beforeExpressionPush(final Throwable throwable) { }
    
    @Hook
    private static Hook.Result assertPhysical(final ProblemDescriptorBase $this, final PsiElement element) = Hook.Result.NULL;
    
    @Hook(at = @At(method = @At.MethodInsn(name = ASMHelper._INIT_)), before = false)
    private static Hook.Result _init_(
            final ProblemDescriptorBase $this,
            @Hook.Reference PsiElement startElement,
            @Hook.Reference PsiElement endElement,
            final String descriptionTemplate,
            final LocalQuickFix fixes[],
            final ProblemHighlightType highlightType,
            final boolean isAfterEndOfLine,
            @Hook.Reference @Nullable TextRange rangeInElement,
            final boolean showTooltip,
            final boolean onTheFly) {
        final PsiElement sourceStartElement = startElement, sourceEndElement = endElement;
        startElement = physicalElement(startElement);
        endElement = physicalElement(endElement);
        if (startElement != sourceStartElement || endElement != sourceEndElement)
            rangeInElement = null;
        if (startElement != null && startElement == endElement && startElement.getLanguage() == JavaLanguage.INSTANCE)
            while (startElement.getParent() != null && !(startElement.getParent() instanceof PsiClass) && startElement.getTextLength() == 0)
                startElement = endElement = startElement.getParent();
        return { };
    }
    
    private static PsiElement physicalElement(final PsiElement element) {
        if (element instanceof LightElement lightElement)
            return lightElement.equivalents().stream()
                    .filter(PsiAnnotation.class::isInstance)
                    .findFirst()
                    .or(() -> lightElement.equivalents().stream().findFirst())
                    .orElse(element);
        else if (element.getContainingFile() instanceof DummyHolder) {
            PsiElement outer = element.getContainingFile().getContext();
            while (outer != null) {
                if (!(outer.getContainingFile() instanceof DummyHolder))
                    return outer;
                outer = outer.getContainingFile().getContext();
            }
        }
        return element;
    }
    
    @Override
    public Class<? extends LocalInspectionTool>[] getInspectionClasses() = new Class[]{ Handler.class };
    
    @Hook
    private static Hook.Result getTokenizer(final JavaSpellcheckingStrategy $this, final PsiElement element)
            = Hook.Result.falseToVoid(amadeus.maho.lang.idea.handler.base.Handler.Marker.baseHandlers().stream().anyMatch(handler -> handler.isSuppressedSpellCheckingFor(element)), SpellcheckingStrategy.EMPTY_TOKENIZER);
    
}
