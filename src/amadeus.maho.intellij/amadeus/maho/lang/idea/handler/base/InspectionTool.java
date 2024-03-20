package amadeus.maho.lang.idea.handler.base;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
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
import amadeus.maho.util.dynamic.LookupHelper;

import kotlin.jvm.functions.Function0;

import static amadeus.maho.lang.idea.IDEAContext.requiresMaho;
import static amadeus.maho.util.bytecode.Bytecodes.ATHROW;

@TransformProvider
public interface InspectionTool {
    
    sealed interface Checker permits BaseSyntaxHandler, BaseHandler { }
    
    @TransformProvider
    interface Provider {
        
        Method
                annotationCheck = LookupHelper.<BaseHandler, PsiElement, Annotation, PsiAnnotation, ProblemsHolder, QuickFixFactory>methodV6(BaseHandler::check),
                syntaxCheck     = LookupHelper.<BaseSyntaxHandler, PsiElement, ProblemsHolder, QuickFixFactory, Boolean>methodV5(BaseSyntaxHandler::check);
        
        private static Collection<Checker> checkers() = Stream.concat(
                HandlerSupport.overrideMap[Handler.Marker.baseHandlers()][annotationCheck].stream(),
                HandlerSupport.overrideMap[List.copyOf(Syntax.Marker.syntaxHandlers().values())][syntaxCheck].stream()
        ).toList();
        
        @Hook
        private static void registerToolProviders(final InspectionToolRegistrar $this, final Map<Object, List<Function0<InspectionToolWrapper<?, ?>>>> factories)
                = factories[Provider.class] = checkers().stream().map(checker -> (Function0<InspectionToolWrapper<?, ?>>) () -> new LocalInspectionToolWrapper(new MahoLocalInspectionTool(checker)) {
            @Override
            public LocalInspectionToolWrapper createCopy() = { new MahoLocalInspectionTool(checker) };
        }).toList();
        
    }
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class MahoLocalInspectionTool extends AbstractBaseJavaLocalInspectionTool {
        
        @Getter
        @RequiredArgsConstructor
        @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
        public static class Checker extends JavaElementVisitor {
            
            InspectionTool.Checker checker;
            
            ProblemsHolder holder;
            
            boolean isOnTheFly;
            
            QuickFixFactory quickFix = QuickFixFactory.getInstance();
            
            @Override
            public void visitReferenceExpression(final PsiReferenceExpression expression) = visitElement(expression);
            
            @Override
            public void visitElement(final PsiElement element) {
                super.visitElement(element);
                if (requiresMaho(element))
                    if (checker instanceof BaseSyntaxHandler syntaxHandler)
                        syntaxHandler.check(element, holder, quickFix, isOnTheFly);
                    else if (element instanceof PsiModifierListOwner owner && checker instanceof BaseHandler annotationHandler)
                        HandlerSupport.getAnnotationsByType(owner, annotationHandler.handler().value()).forEach(tuple -> annotationHandler.check(element, tuple.v1, tuple.v2, holder, quickFix));
            }
            
        }
        
        InspectionTool.Checker checker;
        
        String name = checker.getClass().getSimpleName().replaceLast("Handler", "");
        
        @Override
        public String getDisplayName() = name;
        
        @Override
        public String getShortName() = name;
        
        @Override
        public HighlightDisplayLevel getDefaultLevel() = HighlightDisplayLevel.ERROR;
        
        @Override
        public String[] getGroupPath() = { "Maho" };
        
        @Override
        public String getGroupDisplayName() = InspectionsBundle.message("group.names.compiler.issues");
        
        @Override
        public boolean isEnabledByDefault() = true;
        
        @Override
        public MahoLocalInspectionTool.Checker buildVisitor(final ProblemsHolder holder, final boolean isOnTheFly) = { checker, holder, isOnTheFly };
        
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
    
    @Hook
    private static Hook.Result getTokenizer(final JavaSpellcheckingStrategy $this, final PsiElement element)
            = Hook.Result.falseToVoid(amadeus.maho.lang.idea.handler.base.Handler.Marker.baseHandlers().stream().anyMatch(handler -> handler.isSuppressedSpellCheckingFor(element)), SpellcheckingStrategy.EMPTY_TOKENIZER);
    
}
