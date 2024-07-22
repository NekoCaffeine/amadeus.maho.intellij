package amadeus.maho.lang.idea.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.GenericsHighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMethodUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.ImplementAbstractClassMethodsFix;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.DataFlowInspectionBase;
import com.intellij.codeInspection.dataFlow.TypeConstraints;
import com.intellij.codeInspection.dataFlow.inference.ExpressionRange;
import com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.java.JavaDfaValueFactory;
import com.intellij.codeInspection.dataFlow.java.inst.AssignInstruction;
import com.intellij.codeInspection.dataFlow.java.inst.JvmPushInstruction;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.ArrayElementDescriptor;
import com.intellij.codeInspection.dataFlow.lang.ir.PopInstruction;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.core.JavaPsiBundle;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.expression.ArrayInitializerEvaluator;
import com.intellij.debugger.engine.evaluation.expression.BoxingEvaluator;
import com.intellij.debugger.engine.evaluation.expression.DisableGC;
import com.intellij.debugger.engine.evaluation.expression.Evaluator;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.NewArrayInstanceEvaluator;
import com.intellij.debugger.engine.evaluation.expression.TypeEvaluator;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.findUsages.JavaFindUsagesHelper;
import com.intellij.find.findUsages.JavaMethodFindUsagesOptions;
import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingMode;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.formatting.WrapType;
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.parser.BasicDeclarationParser;
import com.intellij.lang.java.parser.BasicOldExpressionParser;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameterListOwner;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.formatter.java.AbstractJavaBlock;
import com.intellij.psi.formatter.java.ArrayInitializerBlocksBuilder;
import com.intellij.psi.formatter.java.BlockContainingJavaBlock;
import com.intellij.psi.formatter.java.JavaSpacePropertyProcessor;
import com.intellij.psi.formatter.java.SimpleJavaBlock;
import com.intellij.psi.impl.BlockSupportImpl;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.impl.source.AbstractBasicJavaElementTypeFactory;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiPolyVariantCachingReference;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.java.PsiCodeBlockImpl;
import com.intellij.psi.impl.source.tree.java.PsiExpressionListImpl;
import com.intellij.psi.impl.source.tree.java.PsiNewExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.processor.MethodsProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IReparseableElementTypeBase;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CharTable;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.idea.handler.base.BaseSyntaxHandler;
import amadeus.maho.lang.idea.handler.base.HandlerSupport;
import amadeus.maho.lang.idea.handler.base.JavaExpressionIndex;
import amadeus.maho.lang.idea.handler.base.Syntax;
import amadeus.maho.lang.idea.light.LightElementReference;
import amadeus.maho.lang.idea.light.LightMethod;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.control.LinkedIterator;
import amadeus.maho.util.runtime.DebugHelper;

import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;

import static amadeus.maho.lang.idea.IDEAContext.computeReadActionIgnoreDumbMode;
import static amadeus.maho.lang.idea.handler.AssignHandler.PRIORITY;
import static amadeus.maho.lang.idea.handler.base.JavaExpressionIndex.IndexTypes.ASSIGN_NEW;
import static com.intellij.codeInspection.ProblemHighlightType.*;
import static com.intellij.lang.PsiBuilderUtil.*;
import static com.intellij.lang.java.parser.BasicJavaParserUtil.error;
import static com.intellij.lang.java.parser.JavaParserUtil.*;
import static com.intellij.psi.JavaTokenType.*;
import static com.intellij.psi.impl.source.tree.JavaElementType.CODE_BLOCK;

@TransformProvider
@Syntax(priority = PRIORITY)
public class AssignHandler extends BaseSyntaxHandler {
    
    public static final int PRIORITY = 1 << 8;
    
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class VarargsMethodCandidateInfo extends MethodCandidateInfo {
        
        public VarargsMethodCandidateInfo(final PsiMethod method, final PsiSubstitutor substitutor, final PsiExpressionList argumentList, final PsiElement context, final LanguageLevel level)
                = super(method, substitutor, false, false, argumentList, context, argumentList.getExpressionTypes(), null, level);
        
        @Override
        public boolean isVarargs() = true;
        
        @Override
        public int getApplicabilityLevel() = ApplicabilityLevel.VARARGS;
        
        @Override
        public int getPertinentApplicabilityLevel(final @Nullable Map<MethodCandidateInfo, PsiSubstitutor> map) = ApplicabilityLevel.VARARGS;
        
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void _init_(final ExpressionRange $this, final int start, final int end) {
        if (start == 0)
            DebugHelper.breakpoint();
    }
    
    @Hook(value = JavaFindUsagesHelper.class, isStatic = true)
    private static void processElementUsages(final PsiElement element, final FindUsagesOptions options, final Processor<? super UsageInfo> processor) {
        if (options instanceof JavaMethodFindUsagesOptions methodOptions && element instanceof PsiMethod target)
            if (target.isConstructor()) {
                final Project project = PsiUtilCore.getProjectInReadAction(element);
                final Map<VirtualFile, int[]> mapping = computeReadActionIgnoreDumbMode(() -> {
                    final HashMap<VirtualFile, int[]> offsets = { };
                    FileBasedIndex.getInstance().processValues(JavaExpressionIndex.INDEX_ID, ASSIGN_NEW.name(), null, (file, value) -> {
                        ProgressManager.checkCanceled();
                        offsets[file] = value.array();
                        return true;
                    }, GlobalSearchScopeUtil.toGlobalSearchScope(options.searchScope, project));
                    return offsets;
                });
                if (!mapping.isEmpty()) {
                    final PsiManager manager = PsiManager.getInstance(project);
                    manager.runInBatchFilesMode(() -> {
                        mapping.entrySet().stream().anyMatch(entry -> {
                            ProgressManager.checkCanceled();
                            return !computeReadActionIgnoreDumbMode(() -> {
                                if (manager.findFile(entry.getKey()) instanceof PsiJavaFile file)
                                    for (final int offset : entry.getValue()) {
                                        final @Nullable PsiJavaToken token = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiJavaToken.class, false);
                                        if (token != null) {
                                            if (token.getParent() instanceof PsiExpressionList list && list.getParent() instanceof PsiNewExpression expression &&
                                                expression.getManager().areElementsEquivalent(expression.resolveMethod(), element))
                                                if (!(Privilege) JavaFindUsagesHelper.addResult(expression, options, processor))
                                                    return false;
                                        }
                                    }
                                return true;
                            });
                        });
                        return null;
                    });
                }
            }
    }
    
    @Hook
    private static Hook.Result getReference(final LeafPsiElement $this)
            = Hook.Result.nullToVoid($this instanceof PsiJavaToken token && token.getParent() != null && token.getParent().getParent() instanceof PsiNewExpressionImpl expression && isNewExpressionFromArrayInitializer(expression) ?
            new LightElementReference(expression, incompleteCode -> (JavaResolveResult[]) expression.getConstructorFakeReference().multiResolve(incompleteCode), expression) : null);
    
    private static final ThreadLocal<Boolean> parsingNewExpressionLocal = ThreadLocal.withInitial(() -> false);
    
    @Hook(at = @At(method = @At.MethodInsn(name = "parseArrayInitializer")))
    private static void parseNew_$Before(final BasicOldExpressionParser $this, final PsiBuilder builder, final @Nullable PsiBuilder.Marker marker) = parsingNewExpressionLocal.set(true);
    
    @Hook(at = @At(method = @At.MethodInsn(name = "parseArrayInitializer")), before = false)
    private static void parseNew_$After(final BasicOldExpressionParser $this, final PsiBuilder builder, final @Nullable PsiBuilder.Marker marker) = parsingNewExpressionLocal.remove();
    
    @Hook
    private static Hook.Result parseArrayInitializer(final BasicOldExpressionParser $this, final PsiBuilder builder, final IElementType type,
            final Function<? super PsiBuilder, PsiBuilder.Marker> elementParser, final String missingElementKey) {
        final AbstractBasicJavaElementTypeFactory.JavaElementTypeContainer container = (Privilege) $this.myJavaElementTypeContainer;
        if (type == container.ARRAY_INITIALIZER_EXPRESSION) {
            if (parsingNewExpressionLocal.get()?.booleanValue() ?? false)
                return Hook.Result.VOID;
            final PsiBuilder.Marker newExpr = builder.mark();
            final PsiBuilder.Marker list = builder.mark();
            builder.advanceLexer();
            boolean first = true;
            while (true) {
                if (builder.getTokenType() == JavaTokenType.RBRACE) {
                    builder.advanceLexer();
                    break;
                }
                if (builder.getTokenType() == null) {
                    error(builder, JavaPsiBundle.message("expected.rbrace"));
                    break;
                }
                if (elementParser.apply(builder) == null) {
                    if (builder.getTokenType() == JavaTokenType.COMMA) {
                        if (first && builder.lookAhead(1) == JavaTokenType.RBRACE) {
                            advance(builder, 2);
                            break;
                        }
                        builder.error(JavaPsiBundle.message(missingElementKey));
                    } else if (builder.getTokenType() != JavaTokenType.RBRACE) {
                        error(builder, JavaPsiBundle.message("expected.rbrace"));
                        break;
                    }
                }
                first = false;
                final IElementType tokenType = builder.getTokenType();
                if (!expect(builder, JavaTokenType.COMMA) && tokenType != JavaTokenType.RBRACE)
                    error(builder, JavaPsiBundle.message("expected.comma"));
            }
            list.done(container.EXPRESSION_LIST);
            final PsiBuilder.Marker typeArgumentList = builder.mark();
            typeArgumentList.done(container.REFERENCE_PARAMETER_LIST);
            newExpr.done(container.NEW_EXPRESSION);
            return { newExpr };
        }
        return Hook.Result.VOID;
    }
    
    @Hook(value = PsiScopesUtil.class, isStatic = true, forceReturn = true)
    private static void processDummyConstructor(final MethodsProcessor processor, final PsiClass aClass) {
        if (!(aClass instanceof PsiAnonymousClass) && aClass.getConstructors().length == 0 && aClass.getName() != null)
            processor.forceAddResult(CachedValuesManager.getCachedValue(aClass, () -> CachedValueProvider.Result.create(makeDummyConstructor(aClass), aClass)));
    }
    
    private static LightMethod makeDummyConstructor(final PsiClass target) {
        assert target.getName() != null;
        final LightMethod result = { target, target.getName() };
        result.setConstructor(true);
        result.setNavigationElement(target);
        result.setContainingClass(target);
        result.addModifier(PsiModifier.PUBLIC);
        result.setMethodKind("DummyConstructor");
        return result;
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static int getChildRole(final int capture, final PsiExpressionListImpl $this, final ASTNode child) {
        if (capture == ChildRoleBase.NONE && $this.getParent() instanceof PsiNewExpressionImpl expression && isNewExpressionFromArrayInitializer(expression)) {
            final IElementType elementType = child.getElementType();
            if (elementType == JavaTokenType.LBRACE)
                return ChildRole.LBRACE;
            if (elementType == JavaTokenType.RBRACE)
                return ChildRole.RBRACE;
        }
        return capture;
    }
    
    @Hook
    private static Hook.Result visitExpressionList(final JavaSpacePropertyProcessor $this, final PsiExpressionList list) {
        final int myRole1 = (Privilege) $this.myRole1, myRole2 = (Privilege) $this.myRole2;
        final CommonCodeStyleSettings mySettings = (Privilege) $this.mySettings;
        if (myRole1 == ChildRole.LBRACE && myRole2 == ChildRole.RBRACE) {
            (Privilege) $this.createSpaceInCode(mySettings.SPACE_WITHIN_EMPTY_ARRAY_INITIALIZER_BRACES);
        } else if (myRole2 == ChildRole.RBRACE) {
            final boolean space = myRole1 == ChildRole.COMMA || mySettings.SPACE_WITHIN_ARRAY_INITIALIZER_BRACES;
            if (mySettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE && list.getExpressionCount() > 1)
                (Privilege) $this.createSpaceWithLinefeedIfListWrapped(list, space);
            else
                (Privilege) $this.createSpaceInCode(space);
        } else if (myRole1 == ChildRole.LBRACE) {
            final boolean space = true;
            if (mySettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE && list.getExpressionCount() > 1)
                (Privilege) $this.createSpaceWithLinefeedIfListWrapped(list, space);
            else
                (Privilege) $this.createSpaceInCode(space);
        } else
            return Hook.Result.VOID;
        return Hook.Result.NULL;
    }
    
    @Hook
    private static Hook.Result processChild(final AbstractJavaBlock $this, final List<Block> result, final ASTNode child, final AlignmentStrategy alignmentStrategy, final Wrap defaultWrap, final Indent childIndent, final int childOffset) {
        final ASTNode node = $this.getNode();
        if (child.getElementType() == JavaTokenType.LBRACE && node instanceof PsiExpressionList list && list.getParent() instanceof PsiNewExpression newExpression && isNewExpressionFromArrayInitializer(newExpression)) {
            result.addAll(new ArrayInitializerBlocksBuilder(node, (Privilege) $this.myBlockFactory).buildBlocks());
            return { list.getLastChild() };
        }
        if (child instanceof PsiCodeBlockImpl codeBlock && isAssignCodeBlock(codeBlock)) {
            final Wrap wrap = Wrap.createWrap(WrapType.NORMAL, false);
            result.add(new SimpleJavaBlock(child, wrap, alignmentStrategy, Indent.getNormalIndent(), (Privilege) $this.mySettings, (Privilege) $this.myJavaSettings, (Privilege) $this.myFormattingMode));
            return { child };
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result createJavaBlock(final AbstractJavaBlock $this, final ASTNode child, final CommonCodeStyleSettings settings, final JavaCodeStyleSettings javaSettings, final @Nullable Indent indent, final Wrap wrap,
            final AlignmentStrategy alignmentStrategy, final int startOffset, final FormattingMode formattingMode) {
        final ASTNode node = $this.getNode();
        if (child.getElementType() == JavaTokenType.LBRACE && node instanceof PsiExpressionList list && list.getParent() instanceof PsiNewExpression newExpression && isNewExpressionFromArrayInitializer(newExpression)) {
            final Alignment alignment = alignmentStrategy.getAlignment(JavaElementType.ARRAY_INITIALIZER_EXPRESSION);
            return { new BlockContainingJavaBlock(child, wrap, alignment, indent == null ? Indent.getNoneIndent() : indent, settings, javaSettings, formattingMode) };
        }
        return Hook.Result.VOID;
    }
    
    public static LightMethod arrayInitializerMethod(final Project project, final LanguageLevel level, final PsiArrayType arrayType) {
        final PsiClass arrayClass = PsiElementFactory.getInstance(project).getArrayClass(level);
        final Map<PsiArrayType, LightMethod> cache = CachedValuesManager.getProjectPsiDependentCache(arrayClass, _ -> new ConcurrentHashMap<>());
        return cache.computeIfAbsent(arrayType, key -> makeArrayInitializerMethod(arrayClass, key));
    }
    
    private static LightMethod makeArrayInitializerMethod(final PsiClass arrayClass, final PsiArrayType arrayType) {
        final LightMethod result = { arrayClass, arrayType.getPresentableText(false) };
        result.setContainingClass(arrayClass);
        result.addParameter("elements", arrayType, true);
        result.addModifier(PsiModifier.PUBLIC);
        result.setMethodKind("ArrayInitializer");
        return result;
    }
    
    @Hook(value = HighlightMethodUtil.class, isStatic = true)
    private static Hook.Result checkNewExpression(final Project project, final PsiNewExpression expression, final @Nullable PsiType type, final JavaSdkVersion version, final Consumer<? super HighlightInfo.Builder> errorSink) {
        if (isNewExpressionFromArrayInitializer(expression) && expression.getType() instanceof PsiArrayType arrayType && expression.getArgumentList() instanceof PsiExpressionList argumentList) {
            final PsiType componentType = arrayType.getComponentType();
            Stream.of(argumentList.getExpressions())
                    .map(expr -> (Privilege) HighlightUtil.checkAssignability(componentType, expr.getType(), expr, expr.getTextRange(), 0))
                    .nonnull()
                    .forEach(errorSink);
        }
        return Hook.Result.VOID;
    }
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result computeRange(final PsiElement element) {
        if (element instanceof PsiNewExpression newExpression && isNewExpressionFromArrayInitializer(newExpression))
            return { element.getTextRange() };
        return Hook.Result.VOID;
    }
    
    @Hook(value = DataFlowInspectionBase.class, isStatic = true)
    private static Hook.Result getElementToHighlight(final PsiElement element) {
        if (element instanceof PsiNewExpression newExpression && isNewExpressionFromArrayInitializer(newExpression))
            return { newExpression };
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result registerNewExpressionError(final BaseInspectionVisitor $this, final PsiNewExpression expression, final Object... infos) {
        if (isNewExpressionFromArrayInitializer(expression)) {
            (Privilege) $this.registerError(expression, infos);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook(value = GenericsHighlightUtil.class, isStatic = true, forceReturn = true)
    private static @Nullable HighlightInfo.Builder checkGenericArrayCreation(final PsiElement element, final @Nullable PsiType type) = null;
    
    @Hook(value = GenericsHighlightUtil.class, isStatic = true, at = @At(method = @At.MethodInsn(name = "range")), capture = true)
    private static PsiElement checkTypeParameterInstantiation(final PsiElement capture, final PsiNewExpression expression)
            = isNewExpressionFromArrayInitializer(expression) ? expression : capture;
    
    @Hook(value = PsiScopesUtil.class, isStatic = true)
    private static Hook.Result setupAndRunProcessor(final MethodsProcessor processor, final PsiCallExpression call, final boolean dummyImplicitConstructor) {
        if (call instanceof PsiNewExpression newExpression && isNewExpressionFromArrayInitializer(newExpression) &&
            newExpression.getType() instanceof PsiArrayType arrayType && newExpression.getArgumentList() instanceof PsiExpressionList argumentList) {
            final LightMethod arrayInitializerMethod = arrayInitializerMethod(call.getProject(), PsiUtil.getLanguageLevel(call), arrayType);
            processor.setIsConstructor(true);
            processor.setAccessClass(arrayInitializerMethod.getContainingClass());
            processor.setArgumentList(argumentList);
            processor.obtainTypeArguments(newExpression);
            processor.forceAddResult(arrayInitializerMethod);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook(value = LambdaUtil.class, isStatic = true)
    private static Hook.Result getFunctionalInterfaceType(final PsiElement expression, final boolean tryToSubstitute) {
        if (expression.getParent() instanceof PsiExpressionList expressionList && expressionList.getParent() instanceof PsiNewExpression newExpression && newExpression.isArrayCreation() &&
            newExpression.getType() instanceof PsiArrayType arrayType)
            return { arrayType.getComponentType() };
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result getConstructorFakeReference(final PsiNewExpressionImpl $this) = Hook.Result.nullToVoid(isNewExpressionFromArrayInitializer($this) ?
            CachedValuesManager.getProjectPsiDependentCache($this, it -> new PsiPolyVariantCachingReference() {
                
                @Override
                public JavaResolveResult[] resolveInner(final boolean incompleteCode, final PsiFile containingFile) {
                    final PsiType type = $this.getType();
                    final @Nullable PsiExpressionList argumentList = $this.getArgumentList();
                    if (argumentList != null)
                        if (type instanceof PsiClassType classType) {
                            final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(containingFile.getProject()).getResolveHelper();
                            final JavaResolveResult constructor = resolveHelper.resolveConstructor(classType, argumentList, it);
                            return constructor != JavaResolveResult.EMPTY ? new JavaResolveResult[]{ constructor } : resolveHelper.getReferencedMethodCandidates(it, true);
                        } else if (type instanceof PsiArrayType arrayType) {
                            final LanguageLevel level = PsiUtil.getLanguageLevel($this);
                            final LightMethod arrayInitializerMethod = arrayInitializerMethod(containingFile.getProject(), level, arrayType);
                            final VarargsMethodCandidateInfo candidateInfo = { arrayInitializerMethod, PsiSubstitutor.EMPTY, argumentList, $this, level };
                            return { candidateInfo };
                        }
                    return JavaResolveResult.EMPTY_ARRAY;
                }
                
                @Override
                public PsiElement getElement() = it;
                
                @Override
                public @Nullable TextRange getRangeInElement() = null;
                
                @Override
                public String getCanonicalText() { throw new UnsupportedOperationException(); }
                
                @Override
                public @Nullable PsiElement handleElementRename(final String newElementName) = null;
                
                @Override
                public @Nullable PsiElement bindToElement(final PsiElement element) = null;
                
                @Override
                public int hashCode() = getElement().hashCode();
                
                @Override
                public boolean equals(final Object obj) = obj instanceof PsiPolyVariantCachingReference reference && getElement() == reference.getElement();
                
            }) : null);
    
    @Hook
    private static Hook.Result getQualifier(final PsiNewExpressionImpl $this) = Hook.Result.falseToVoid(isNewExpressionFromArrayInitializer($this), null);
    
    @Hook
    private static Hook.Result getClassReference(final PsiNewExpressionImpl $this) = Hook.Result.nullToVoid(referenceFromContext($this));
    
    @Hook
    private static Hook.Result getClassOrAnonymousClassReference(final PsiNewExpressionImpl $this) = Hook.Result.nullToVoid(referenceFromContext($this));
    
    public static @Nullable PsiJavaCodeReferenceElement referenceFromContext(final PsiNewExpression expression) = isNewExpressionFromArrayInitializer(expression) ?
            CachedValuesManager.getProjectPsiDependentCache(expression, AssignHandler::lookupTypeReference) : null;
    
    @Hook
    private static Hook.Result doGetType(final PsiNewExpressionImpl $this, final PsiAnnotation stopAt) = Hook.Result.nullToVoid(isNewExpressionFromArrayInitializer($this) ?
            CachedValuesManager.getProjectPsiDependentCache($this, it -> computeReadActionIgnoreDumbMode(() -> lookupType(it))) : null);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static boolean isArrayCreation(final boolean capture, final PsiNewExpression $this) = capture || isNewExpressionFromArrayInitializer($this) && $this.getType() instanceof PsiArrayType;
    
    public static boolean isNewExpressionFromArrayInitializer(final PsiNewExpression expression)
            = expression.getFirstChild() instanceof PsiExpressionList list && list.getFirstChild() instanceof PsiJavaToken token && token.getTokenType() == JavaTokenType.LBRACE;
    
    private static PsiElement parent(final PsiExpression expression) = switch (expression.getContext() ?? expression) {
        case DummyHolder dummyHolder -> dummyHolder.getContext() ?? expression;
        case PsiElement element      -> element;
    };
    
    private static @Nullable PsiJavaCodeReferenceElement lookupTypeReference(final PsiNewExpression expression) {
        if (expression.getType() instanceof PsiArrayType)
            return null;
        final PsiElement parent = parent(expression);
        if (parent instanceof PsiReturnStatement) {
            final PsiParameterListOwner owner = PsiTreeUtil.getContextOfType(parent, PsiParameterListOwner.class);
            if (owner instanceof PsiMethod method)
                return method.getReturnTypeElement()?.getInnermostComponentReferenceElement() ?? null;
        } else if (parent instanceof PsiVariable variable) {
            final @Nullable PsiTypeElement typeElement = variable.getTypeElement();
            if (typeElement != null && !typeElement.isInferredType())
                return typeElement.getInnermostComponentReferenceElement();
        } else if (parent instanceof PsiAssignmentExpression assignment)
            return assignment.getLExpression() instanceof PsiJavaCodeReferenceElement reference && reference.resolve() instanceof PsiVariable variable &&
                   variable.getTypeElement() instanceof PsiTypeElement typeElement && !typeElement.isInferredType() ? typeElement.getInnermostComponentReferenceElement() : null;
        return null;
    }
    
    private static @Nullable PsiType lookupType(final PsiNewExpression expression) {
        final PsiElement parent = parent(expression);
        if (parent instanceof PsiReturnStatement) {
            final PsiParameterListOwner owner = PsiTreeUtil.getContextOfType(parent, PsiParameterListOwner.class);
            if (owner instanceof PsiMethod method)
                return method.getReturnType();
        } else if (parent instanceof PsiVariable variable) {
            final @Nullable PsiTypeElement typeElement = variable.getTypeElement();
            if (typeElement != null && !typeElement.isInferredType())
                return HandlerSupport.unwrapType(variable);
        } else if (parent instanceof PsiAssignmentExpression assignment)
            return assignment.getLExpression().getType();
        else if (parent instanceof PsiExpressionList expressionList && expressionList.getParent() instanceof PsiNewExpression newExpression &&
                 newExpression.getType() instanceof PsiArrayType arrayType)
            return arrayType.getComponentType();
        return null;
    }
    
    @Hook(value = PsiDiamondTypeUtil.class, isStatic = true)
    private static Hook.Result canCollapseToDiamond(final PsiNewExpression expression, final PsiNewExpression context, final @Nullable PsiType expectedType, final boolean skipDiamonds)
            = Hook.Result.falseToVoidReverse(isNewExpressionFromArrayInitializer(expression));
    
    @Hook(target = "com.intellij.codeInspection.RedundantExplicitVariableTypeInspection$1")
    private static Hook.Result visitLocalVariable(final JavaElementVisitor $this, final PsiLocalVariable variable) {
        if (variable.getInitializer() instanceof PsiNewExpression newExpression && isNewExpressionFromArrayInitializer(newExpression))
            return Hook.Result.NULL;
        return Hook.Result.VOID;
    }
    
    @Hook(target = "com.intellij.codeInspection.dataFlow.jvm.SpecialField$1")
    private static Hook.Result fromInitializer(final SpecialField $this, final PsiExpression expression) {
        if (expression instanceof PsiNewExpression newExpression && newExpression.isArrayCreation() && newExpression.getArgumentList() instanceof PsiExpressionList argumentList)
            return { DfTypes.intValue(argumentList.getExpressionCount()) };
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result visitNewExpression(final ControlFlowAnalyzer $this, final PsiNewExpression expression) {
        if (expression.isArrayCreation() && expression.getArgumentList() instanceof PsiExpressionList argumentList) {
            (Privilege) $this.startElement(expression);
            final PsiType type = expression.getType();
            final PsiType componentType = type instanceof PsiArrayType arrayType ? arrayType.getComponentType() : null;
            DfaVariableValue var = (Privilege) $this.getTargetVariable(expression);
            DfaVariableValue arrayWriteTarget = var;
            if (var == null)
                var = (Privilege) $this.createTempVariable(type);
            final PsiExpression[] initializers = argumentList.getExpressions();
            final DfaValueFactory factory = (Privilege) $this.getFactory();
            if (arrayWriteTarget != null) {
                final PsiVariable arrayVariable = ObjectUtils.tryCast(arrayWriteTarget.getPsiVariable(), PsiVariable.class);
                if (arrayWriteTarget.isFlushableByCalls() ||
                    arrayVariable == null ||
                    VariableAccessUtils.variableIsUsed(arrayVariable, expression) ||
                    ExpressionUtils.getConstantArrayElements(arrayVariable) != null ||
                    !(ArrayElementDescriptor.getArrayElementValue(factory, arrayWriteTarget, 0) instanceof DfaVariableValue))
                    arrayWriteTarget = null;
            }
            final DfType arrayType = SpecialField.ARRAY_LENGTH.asDfType(DfTypes.intValue(initializers.length))
                    .meet(type == null ? DfTypes.OBJECT_OR_NULL : TypeConstraints.exact(type).asDfType())
                    .meet(DfTypes.LOCAL_OBJECT);
            if (arrayWriteTarget != null) {
                (Privilege) $this.addInstruction(new JvmPushInstruction(arrayWriteTarget, null, true));
                (Privilege) $this.push(arrayType, expression);
                (Privilege) $this.addInstruction(new AssignInstruction(expression, arrayWriteTarget));
                int index = 0;
                for (final PsiExpression initializer : initializers) {
                    DfaValue target = null;
                    if (index < (Privilege) ControlFlowAnalyzer.MAX_ARRAY_INDEX_FOR_INITIALIZER)
                        target = Objects.requireNonNull(ArrayElementDescriptor.getArrayElementValue(factory, arrayWriteTarget, index));
                    index++;
                    (Privilege) $this.addInstruction(new JvmPushInstruction(target == null ? factory.getUnknown() : target, null, true));
                    initializer.accept($this);
                    if (componentType != null)
                        (Privilege) $this.generateBoxingUnboxingInstructionFor(initializer, componentType);
                    (Privilege) $this.addInstruction(new AssignInstruction(initializer, null));
                    (Privilege) $this.addInstruction(new PopInstruction());
                }
            } else {
                for (final PsiExpression initializer : initializers) {
                    (Privilege) $this.addInstruction(new JvmPushInstruction(factory.getUnknown(), null, true));
                    initializer.accept($this);
                    if (componentType != null)
                        (Privilege) $this.generateBoxingUnboxingInstructionFor(initializer, componentType);
                    (Privilege) $this.addInstruction(new AssignInstruction(initializer, null));
                    (Privilege) $this.addInstruction(new PopInstruction());
                }
                (Privilege) $this.addInstruction(new JvmPushInstruction(var, null, true));
                (Privilege) $this.push(arrayType, expression);
                (Privilege) $this.addInstruction(new AssignInstruction(expression, var));
            }
            (Privilege) $this.finishElement(expression);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result getIteratedElement(final ControlFlowAnalyzer $this, final PsiType type, final PsiExpression iteratedValue) {
        if (iteratedValue instanceof PsiNewExpression newExpression && newExpression.isArrayCreation() && newExpression.getArgumentList() instanceof PsiExpressionList argumentList)
            return { JavaDfaValueFactory.createCommonValue((Privilege) $this.getFactory(), argumentList.getExpressions(), type) };
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result visitNewExpression(final EvaluatorBuilderImpl.Builder $this, final PsiNewExpression expression) {
        if (expression.isArrayCreation() && expression.getArgumentList() instanceof PsiExpressionList argumentList) {
            final PsiExpression initializers[] = argumentList.getExpressions();
            final Evaluator evaluators[] = new Evaluator[initializers.length];
            final PsiType type = expression.getType();
            final boolean primitive = type instanceof PsiArrayType arrayType && arrayType.getComponentType() instanceof PsiPrimitiveType;
            for (int idx = 0; idx < initializers.length; idx++) {
                final PsiExpression initializer = initializers[idx];
                initializer.accept($this);
                final @Nullable Evaluator result = (Privilege) $this.myResult;
                if (result != null)
                    evaluators[idx] = DisableGC.create(primitive ? (Privilege) EvaluatorBuilderImpl.Builder.handleUnaryNumericPromotion(initializer.getType(), result) : new BoxingEvaluator(result));
                else
                    throw (Privilege) EvaluatorBuilderImpl.Builder.expressionInvalid(initializer);
            }
            (Privilege) ($this.myResult = (Privilege) new ArrayInitializerEvaluator(evaluators));
            if (type != null && !(expression.getParent() instanceof PsiNewExpression))
                (Privilege) ($this.myResult = (Privilege) new NewArrayInstanceEvaluator(new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(type)), null, (Privilege) $this.myResult));
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkReturnStatementType(final PsiReturnStatement statement, final PsiElement parent) {
        if (parent instanceof PsiMethodImpl method && method.getBody() instanceof PsiCodeBlockImpl codeBlock && codeBlock.getFirstChild() instanceof PsiJavaToken token && token.getTokenType() == EQ) {
            final PsiTypeElement typeElement = method.getReturnTypeElement();
            final @Nullable PsiType returnType = method.getReturnType();
            if (typeElement == null || returnType == null || PsiTypes.voidType().equals(returnType) || SelfHandler.isSelfReference(typeElement.getText()))
                return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result isAvailable(final ImplementAbstractClassMethodsFix $this, final Project project, final PsiFile file, final PsiElement startElement, final PsiElement endElement)
            = Hook.Result.falseToVoid(startElement instanceof PsiNewExpressionImpl expression && isNewExpressionFromArrayInitializer(expression), false);
    
    public static boolean isAssignCodeBlock(final PsiCodeBlock codeBlock) = codeBlock.getFirstChild() instanceof PsiJavaToken token && token.getTokenType() == EQ;
    
    @Hook
    private static Hook.Result visitMethod(final JavaSpacePropertyProcessor $this, final PsiMethod method) {
        if ((Privilege) $this.myType2 == CODE_BLOCK && method.getBody() instanceof PsiCodeBlock codeBlock && isAssignCodeBlock(codeBlock)) {
            (Privilege) $this.createSpaceProperty(((Privilege) $this.mySettings).SPACE_AROUND_ASSIGNMENT_OPERATORS, true, 0);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result visitCodeBlock(final JavaSpacePropertyProcessor $this, final PsiCodeBlock block) {
        if ((Privilege) $this.myType1 == EQ) {
            (Privilege) $this.createSpaceInCode(((Privilege) $this.mySettings).SPACE_AROUND_ASSIGNMENT_OPERATORS);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result getFirstBodyElement(final PsiCodeBlockImpl $this) {
        if ($this.getFirstChild() instanceof PsiJavaToken token && token.getTokenType() == EQ)
            return { token.getNextSibling() };
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result getLastBodyElement(final PsiCodeBlockImpl $this) {
        if ($this.getFirstChild() instanceof PsiJavaToken token && token.getTokenType() == EQ)
            return { $this.getLastChild() instanceof PsiJavaToken lastToken && lastToken.getTokenType() == SEMICOLON ? lastToken.getPrevSibling() : $this.getLastChild() };
        return Hook.Result.VOID;
    }
    
    @Hook(value = JavaPsiConstructorUtil.class, isStatic = true)
    public static Hook.Result findThisOrSuperCallInConstructor(final PsiMethod constructor) {
        if (!constructor.isConstructor())
            return Hook.Result.NULL;
        final @Nullable PsiCodeBlock body = constructor.getBody();
        if (body == null)
            return Hook.Result.NULL;
        PsiElement bodyElement = body.getFirstBodyElement();
        while (bodyElement != null && !(bodyElement instanceof PsiStatement))
            bodyElement = bodyElement.getNextSibling();
        final @Nullable PsiExpression expression = bodyElement instanceof PsiExpressionStatement statement ? statement.getExpression() :
                bodyElement instanceof PsiReturnStatement statement ? statement.getReturnValue() : null;
        return { JavaPsiConstructorUtil.isConstructorCall(expression) ? expression : null };
    }
    
    @Hook
    private static Hook.Result parseMethodBody(final BasicDeclarationParser $this, final PsiBuilder builder, final PsiBuilder.Marker declaration, final boolean anno) {
        if (!anno) {
            final IElementType tokenType = builder.getTokenType();
            if (tokenType == EQ) {
                final PsiBuilder.Marker block = builder.mark();
                builder.advanceLexer();
                final PsiBuilder.Marker statement = builder.mark();
                final @Nullable PsiBuilder.Marker expression = ((Privilege) $this.myParser).getExpressionParser().parse(builder);
                if (expression == null)
                    error(builder, JavaErrorBundle.message("expected.expression"));
                semicolon(builder);
                done(statement, JavaElementType.RETURN_STATEMENT);
                done(block, CODE_BLOCK);
                done(declaration, JavaElementType.METHOD);
                return { declaration };
            }
        }
        return Hook.Result.VOID;
    }
    
    @Hook(value = BlockSupportImpl.class, isStatic = true)
    private static Hook.Result tryReparseNode(final IReparseableElementTypeBase reparseable, final ASTNode node, final CharSequence newTextStr, final PsiManager manager, final Language baseLanguage, final CharTable charTable) {
        if (node instanceof PsiCodeBlockImpl codeBlock && codeBlock.getParent() instanceof PsiMethodImpl && newTextStr.charAt(0) == '=') {
            final ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(JavaLanguage.INSTANCE);
            final PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(definition, definition.createLexer(codeBlock.getProject()), newTextStr);
            setLanguageLevel(builder, PsiUtil.getLanguageLevel(codeBlock));
            final PsiBuilder.Marker block = builder.mark();
            builder.advanceLexer();
            final PsiBuilder.Marker statement = builder.mark();
            final PsiBuilder.Marker expr = JavaParser.INSTANCE.getExpressionParser().parse(builder);
            if (expr == null)
                error(builder, JavaErrorBundle.message("expected.expression"));
            semicolon(builder);
            done(statement, JavaElementType.RETURN_STATEMENT);
            while (!builder.eof())
                builder.advanceLexer();
            done(block, CODE_BLOCK);
            final TreeElement newBlock = (TreeElement) builder.getTreeBuilt();
            DummyHolderFactory.createHolder(manager, null, node.getPsi(), charTable).getTreeElement().rawAddChildren(newBlock);
            return { newBlock };
        }
        return Hook.Result.VOID;
    }
    
    @NoArgsConstructor
    public static final class SimplifyBodyFix extends LocalQuickFixAndIntentionActionOnPsiElement {
        
        @Override
        public @IntentionName String getText() = getFamilyName();
        
        @Override
        public String getFamilyName() = "Simplify body";
        
        @Override
        public void invoke(final Project project, final PsiFile file, final @Nullable Editor editor, final PsiElement startElement, final PsiElement endElement) {
            if (isAvailable(project, file, editor, startElement, endElement)) {
                final PsiMethodImpl method = (PsiMethodImpl) startElement;
                final @Nullable PsiCodeBlock body = method.getBody();
                final @Nullable PsiJavaToken lBrace = body?.getLBrace() ?? null;
                if (lBrace != null) {
                    final @Nullable PsiExpression expression = switch (PsiTreeUtil.skipWhitespacesAndCommentsForward(lBrace)) {
                        case PsiReturnStatement returnStatement         -> returnStatement.getReturnValue();
                        case PsiExpressionStatement expressionStatement -> expressionStatement.getExpression();
                        case null,
                             default                                    -> null;
                    };
                    if (expression != null)
                        method.replace(PsiElementFactory.getInstance(project).createMethodFromText(STR."\{methodHeader(method)} = \{expression.getText()};", method.getContainingClass()));
                }
            }
        }
        
        public static String methodHeader(final PsiMethod method) = LinkedIterator.of(PsiElement::getNextSibling, method.getFirstChild()).stream(true)
                .takeWhile(it -> it != method.getBody())
                .map(PsiElement::getText)
                .collect(Collectors.joining())
                .trim();
        
        @Override
        public boolean isAvailable(final Project project, final PsiFile file, final PsiElement startElement, final PsiElement endElement) = check(startElement);
        
        public static boolean check(final PsiElement element) {
            if (element instanceof PsiMethodImpl) {
                final @Nullable PsiCodeBlock body = ((PsiMethod) element).getBody();
                if (body == null || body.getLBrace() == null || body.getStatementCount() != 1)
                    return false;
                final PsiElement forward = PsiTreeUtil.skipWhitespacesAndCommentsForward(body.getLBrace());
                return forward instanceof PsiExpressionStatement || forward instanceof PsiReturnStatement;
            }
            return false;
        }
        
    }
    
    @NoArgsConstructor
    public static final class SimplifyExpressionFix extends LocalQuickFixAndIntentionActionOnPsiElement {
        
        @Override
        public @IntentionName String getText() = getFamilyName();
        
        @Override
        public String getFamilyName() = "Simplify expression";
        
        @Override
        public boolean startInWriteAction() = false;
        
        @Override
        public void invoke(final Project project, final PsiFile file, final @Nullable Editor editor, final PsiElement startElement, final PsiElement endElement) {
            if (isAvailable(project, file, editor, startElement, endElement) && startElement instanceof PsiNewExpressionImpl newExpression && newExpression.getArgumentList() != null)
                WriteCommandAction.writeCommandAction(project, file).run(() -> {
                    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
                    if (newExpression.getQualifier() instanceof PsiReferenceExpressionImpl expression && startElement.getParent() instanceof PsiLocalVariable variable && variable.getTypeElement().isInferredType()) {
                        final @Nullable PsiType type = expression.getType();
                        if (type != null)
                            variable.getTypeElement().replace(factory.createTypeElement(type));
                    }
                    startElement.replace(factory.createExpressionFromText(STR."{ \{Stream.of(newExpression.getArgumentList().getExpressions()).map(PsiElement::getText).collect(Collectors.joining(", "))}}", startElement));
                    UndoUtil.markPsiFileForUndo(file);
                });
        }
        
        @Override
        public boolean isAvailable(final Project project, final PsiFile file, final PsiElement startElement, final PsiElement endElement) = check(startElement);
        
        public static boolean check(final PsiElement element) {
            if (element instanceof PsiNewExpressionImpl expression && !isNewExpressionFromArrayInitializer(expression)) {
                if (expression.getAnonymousClass() != null || expression.getQualifier() != null || expression.getArgumentList() == null)
                    return false;
                final @Nullable PsiJavaCodeReferenceElement reference = expression.getClassReference();
                if (reference == null)
                    return false;
                if (expression.getTypeArguments().length != 0)
                    return false;
                final @Nullable PsiType type = expression.getType();
                if (type == null)
                    return false;
                final PsiElement parent = expression.getParent();
                if (parent instanceof PsiAssignmentExpression assignment && OperatorOverloadingHandler.overloadInfo(assignment) == null)
                    return type.equals(assignment.getLExpression().getType());
                if (parent instanceof PsiReturnStatement) {
                    final @Nullable PsiParameterListOwner owner = PsiTreeUtil.getContextOfType(parent, PsiParameterListOwner.class); // PsiMethod | PsiLambdaExpression
                    if (owner instanceof PsiMethod method)
                        return type.equals(method.getReturnType());
                } else if (parent instanceof PsiVariable variable) {
                    if (!(variable?.getTypeElement()?.isInferredType() ?? false))
                        return type.equals(variable.getType());
                }
            }
            return false;
        }
        
    }
    
    @Override
    public void check(final PsiElement tree, final ProblemsHolder holder, final QuickFixFactory quickFix, final boolean isOnTheFly) {
        if (SimplifyBodyFix.check(tree) && ((PsiMethod) tree).getBody() instanceof PsiCodeBlockImpl codeBlock)
            holder.registerProblem(codeBlock, "Body can be simplified", isOnTheFly ? INFORMATION : WARNING, new SimplifyBodyFix(tree));
        if (SimplifyExpressionFix.check(tree))
            holder.registerProblem(tree, "New expression can be simplified", isOnTheFly ? INFORMATION : WARNING, new SimplifyExpressionFix(tree));
    }
    
}
