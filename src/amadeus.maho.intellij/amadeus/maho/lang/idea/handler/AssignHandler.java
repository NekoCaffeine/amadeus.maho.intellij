package amadeus.maho.lang.idea.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.codeInsight.daemon.impl.quickfix.ImplementAbstractClassMethodsFix;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.findUsages.JavaFindUsagesHelper;
import com.intellij.find.findUsages.JavaMethodFindUsagesOptions;
import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingMode;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.parser.BasicDeclarationParser;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDiamondType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameterListOwner;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
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
import com.intellij.psi.impl.BlockSupportImpl;
import com.intellij.psi.impl.DiffLog;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiPolyVariantCachingReference;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.java.PsiArrayInitializerExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiCodeBlockImpl;
import com.intellij.psi.impl.source.tree.java.PsiExpressionListImpl;
import com.intellij.psi.impl.source.tree.java.PsiNewExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.impl.source.tree.java.PsiReferenceParameterListImpl;
import com.intellij.psi.infos.ClassCandidateInfo;
import com.intellij.psi.scope.processor.MethodsProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScopeUtil;
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
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.idea.handler.base.ASTTraverser;
import amadeus.maho.lang.idea.handler.base.BaseSyntaxHandler;
import amadeus.maho.lang.idea.handler.base.HandlerSupport;
import amadeus.maho.lang.idea.handler.base.JavaExpressionIndex;
import amadeus.maho.lang.idea.handler.base.Syntax;
import amadeus.maho.lang.idea.light.LightElementReference;
import amadeus.maho.lang.idea.light.LightMethod;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.DebugHelper;

import static amadeus.maho.lang.idea.IDEAContext.*;
import static amadeus.maho.lang.idea.handler.AssignHandler.PRIORITY;
import static amadeus.maho.lang.idea.handler.base.JavaExpressionIndex.IndexTypes.ASSIGN_NEW;
import static com.intellij.codeInspection.ProblemHighlightType.*;
import static com.intellij.lang.java.parser.JavaParserUtil.*;
import static com.intellij.psi.JavaTokenType.EQ;
import static com.intellij.psi.TokenType.WHITE_SPACE;

@TransformProvider
@Syntax(priority = PRIORITY)
public class AssignHandler extends BaseSyntaxHandler {
    
    public static final int PRIORITY = 1 << 8;
    
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class PsiArrayInitializerBackNewExpression extends PsiNewExpressionImpl {
        
        public static class ExpressionList extends PsiExpressionListImpl {
            
            @Override
            public int getChildRole(final ASTNode child) {
                final IElementType type = child.getElementType();
                return type == JavaTokenType.LBRACE ? ChildRole.LBRACE : type == JavaTokenType.RBRACE ? ChildRole.RBRACE : super.getChildRole(child);
            }
            
            @Override
            public boolean isValid() = true;
            
        }
        
        public static class ReferenceParameterList extends PsiReferenceParameterListImpl {
            
            @Override
            public int getChildRole(final ASTNode child) {
                final IElementType type = child.getElementType();
                return type == JavaTokenType.LBRACE ? ChildRole.LBRACE : type == JavaTokenType.RBRACE ? ChildRole.RBRACE : super.getChildRole(child);
            }
            
            @Override
            public boolean isValid() = true;
            
        }
        
        @RequiredArgsConstructor
        @FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
        public static class ReferenceElement extends PsiReferenceExpressionImpl {
            
            { markChildAs(this, new ReferenceParameterList()); }
            
            public @Nullable PsiClassType classType() = getParent() instanceof PsiExpression expression && expression.getType() instanceof PsiClassType classType ? classType : null;
            
            @Override
            public PsiType getType() = classType();
            
            @Override
            public String getText() = "";
            
            @Override
            public TextRange getTextRange() = getParent().getTextRange();
            
            @Override
            public String getCanonicalText() = "";
            
            @Override
            public @Nullable String getReferenceName() = null;
            
            @Override
            public JavaResolveResult[] multiResolve(final boolean incompleteCode) {
                final @Nullable PsiClassType classType = classType();
                if (classType == null || classType instanceof PsiClassReferenceType referenceType && referenceType.getReference() == this) // avoid recursion
                    return JavaResolveResult.EMPTY_ARRAY;
                final PsiClassType.ClassResolveResult result = classType.resolveGenerics();
                final @Nullable PsiClass element = result.getElement();
                return element == null ? JavaResolveResult.EMPTY_ARRAY : new JavaResolveResult[]{
                        new ClassCandidateInfo(element, result.getSubstitutor(), PsiResolveHelper.getInstance(getProject()).isAccessible(element, this, null), getContainingFile())
                };
            }
            
            @Override
            public void delete() { }
            
            @Override
            public void deleteChildInternal(final ASTNode child) { }
            
            @Override
            public void deleteChildRange(final PsiElement first, final PsiElement last) { }
            
            @Override
            public void checkDelete() { }
            
            @Override
            public PsiElement bindToElement(final PsiElement element) = this;
            
            @Override
            public String getClassNameText() = PsiNameHelper.getQualifiedClassName(getCanonicalText(), false);
            
            @Override
            public void fullyQualify(final PsiClass targetClass) { }
            
            @Override
            public boolean isValid() = true;
            
        }
        
        final ReferenceElement classReference;
        
        final ReferenceParameterList parameterList;
        
        public PsiArrayInitializerBackNewExpression(final PsiArrayInitializerExpressionImpl source) {
            classReference = { };
            parameterList = { };
            final ExpressionList list = { };
            markEquivalent(this, source);
            firstChild(list, firstChild(source));
            lastChild(list, lastChild(source));
            markChildAs(this, classReference, parameterList, list);
        }
        
        @Override
        public @Nullable PsiType getType() = computeReadActionIgnoreDumbMode(() -> lookupType(this));
        
        @Override
        public PsiReferenceParameterList getTypeArgumentList() = parameterList;
        
        @Override
        public PsiExpression getQualifier() = null;
        
        @Override
        public @Nullable PsiJavaCodeReferenceElement getClassOrAnonymousClassReference() = classReference;
        
        @Override
        public PsiPolyVariantCachingReference getConstructorFakeReference() = CachedValuesManager.getProjectPsiDependentCache(this, it -> new PsiPolyVariantCachingReference() {
            
            @Override
            public JavaResolveResult[] resolveInner(final boolean incompleteCode, final PsiFile containingFile) {
                if (getType() instanceof PsiClassType classType && getArgumentList() != null) {
                    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(containingFile.getProject()).getResolveHelper();
                    final JavaResolveResult constructor = resolveHelper.resolveConstructor(classType, getArgumentList(), it);
                    return constructor != JavaResolveResult.EMPTY ? new JavaResolveResult[]{ constructor } : resolveHelper.getReferencedMethodCandidates(it, true);
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
            
        });
        
        @Override
        public boolean isValid() = true;
        
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
                                            if (token.getParent() instanceof PsiArrayInitializerBackNewExpression.ExpressionList list &&
                                                list.getParent() instanceof PsiArrayInitializerBackNewExpression expression &&
                                                expression.getManager().areElementsEquivalent(expression.resolveMethod(), element))
                                                if (!(Privilege) JavaFindUsagesHelper.addResult(expression, options, processor))
                                                    return false;
                                        } else
                                            DebugHelper.breakpoint();
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
    private static Hook.Result visitExpression(final HighlightVisitorImpl $this, final PsiExpression expression) = Hook.Result.falseToVoid(expression instanceof PsiArrayInitializerBackNewExpression.ReferenceElement, null);
    
    @Hook
    private static Hook.Result getReference(final LeafPsiElement $this)
            = Hook.Result.nullToVoid($this instanceof PsiJavaToken token && token.getParent() != null && token.getParent().getParent() instanceof PsiArrayInitializerBackNewExpression expression ?
            new LightElementReference(expression, incompleteCode -> (JavaResolveResult[]) expression.getConstructorFakeReference().multiResolve(incompleteCode), expression) : null);
    
    @Hook(value = PsiScopesUtil.class, isStatic = true)
    private static Hook.Result processDummyConstructor(final MethodsProcessor processor, final PsiClass aClass) {
        if (!(aClass instanceof PsiAnonymousClass) && aClass.getConstructors().length == 0 && aClass.getName() != null)
            processor.forceAddResult(CachedValuesManager.getCachedValue(aClass, () -> CachedValueProvider.Result.create(makeDummyConstructor(aClass), aClass)));
        return Hook.Result.NULL;
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
    
    // Formatting Support
    
    @Hook
    private static Hook.Result visitMethod(final JavaSpacePropertyProcessor $this, final PsiMethod method) {
        if ((Privilege) $this.myType1 == EQ || (Privilege) $this.myType2 == EQ) {
            (Privilege) $this.createSpaceInCode(((Privilege) $this.mySettings).SPACE_AROUND_ASSIGNMENT_OPERATORS);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
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
        if (child.getElementType() == JavaTokenType.LBRACE && node instanceof PsiArrayInitializerBackNewExpression.ExpressionList parent) {
            result.addAll(new ArrayInitializerBlocksBuilder(node, (Privilege) $this.myBlockFactory).buildBlocks());
            return { parent.getLastChild() };
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result createJavaBlock(final AbstractJavaBlock $this, final ASTNode child, final CommonCodeStyleSettings settings, final JavaCodeStyleSettings javaSettings, final @Nullable Indent indent, final Wrap wrap,
            final AlignmentStrategy alignmentStrategy, final int startOffset, final FormattingMode formattingMode) {
        final ASTNode node = $this.getNode();
        if (child.getElementType() == JavaTokenType.LBRACE && node instanceof PsiArrayInitializerBackNewExpression.ExpressionList) {
            final Alignment alignment = alignmentStrategy.getAlignment(JavaElementType.ARRAY_INITIALIZER_EXPRESSION);
            return { new BlockContainingJavaBlock(child, wrap, alignment, indent == null ? Indent.getNoneIndent() : indent, settings, javaSettings, formattingMode) };
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result nodeReplaced(final DiffLog $this, final ASTNode oldNode, final ASTNode newNode)
            = Hook.Result.falseToVoid(oldNode instanceof PsiArrayInitializerBackNewExpression && newNode instanceof PsiArrayInitializerExpression && oldNode.getText().equals(newNode.getText()), null);
    
    // There is also room for optimization, by limiting the traversal depth and skipping nested expressions
    @Override
    public void transformASTNode(final ASTNode root, final boolean loadingTreeElement) = ASTTraverser.forEach(root, loadingTreeElement, PsiArrayInitializerExpressionImpl.class, AssignHandler::transformArrayInitializerExpression);
    
    private static void transformArrayInitializerExpression(final PsiArrayInitializerExpressionImpl expression) {
        if (type(expression) instanceof PsiArrayInitializerBackNewExpression backNewExpression) {
            replaceMarkChild(myParent(expression), expression, backNewExpression);
            final PsiExpressionListImpl argumentList = (PsiExpressionListImpl) backNewExpression.getArgumentList();
            for (TreeElement arg = (TreeElement) argumentList?.getFirstChild()?.getNode() ?? null; arg != null; arg = arg.getTreeNext())
                myParent(arg, argumentList);
        }
    }
    
    public static @Nullable Object type(final PsiArrayInitializerExpression expression) {
        if (expression == null || expression.getClass() != PsiArrayInitializerExpressionImpl.class)
            return null;
        return CachedValuesManager.getProjectPsiDependentCache(expression, it -> RecursionManager.doPreventingRecursion(expression, false, () -> computeReadActionIgnoreDumbMode(() -> syncType(expression))));
    }
    
    private static PsiElement parent(final PsiExpression expression) = switch (expression.getContext()) {
        case DummyHolder dummyHolder -> dummyHolder.getContext();
        case PsiElement element      -> element;
    };
    
    private static @Nullable Object syncType(final PsiArrayInitializerExpression expression) {
        final PsiElement parent = parent(expression);
        if (parent instanceof PsiReturnStatement) {
            final PsiParameterListOwner owner = PsiTreeUtil.getContextOfType(parent, PsiParameterListOwner.class);
            if (owner instanceof PsiMethod method)
                return calculateType(expression, method.getReturnType());
        } else if (parent instanceof PsiVariable variable)
            return calculateType(expression, resolve(variable));
        else if (parent instanceof PsiAssignmentExpression assignment)
            return calculateType(expression, assignment.getLExpression().getType());
        final @Nullable PsiVariable variable = DefaultValueHandler.defaultVariable(expression);
        if (variable != null)
            return calculateType(expression, resolve(variable));
        return null;
    }
    
    public static @Nullable PsiType lookupType(final PsiExpression expression) {
        final PsiElement parent = parent(expression);
        if (parent instanceof PsiReturnStatement) {
            final PsiParameterListOwner owner = PsiTreeUtil.getContextOfType(parent, PsiParameterListOwner.class);
            if (owner instanceof PsiMethod method)
                return method.getReturnType();
        } else if (parent instanceof PsiVariable variable) {
            final @Nullable PsiTypeElement typeElement = variable.getTypeElement();
            if (typeElement != null && !typeElement.isInferredType())
                return variable.getTypeElement().getType();
        } else if (parent instanceof PsiAssignmentExpression assignment)
            return assignment.getLExpression().getType();
        final @Nullable PsiVariable variable = DefaultValueHandler.defaultVariable(expression);
        if (variable != null)
            return resolve(variable);
        return null;
    }
    
    private static @Nullable PsiType resolve(final PsiVariable variable) = HandlerSupport.unwrapType(variable);
    
    public static @Nullable Object calculateType(final PsiArrayInitializerExpression expression, final @Nullable PsiType type) {
        if (type instanceof PsiClassType && expression instanceof PsiArrayInitializerExpressionImpl impl)
            return new PsiArrayInitializerBackNewExpression(impl);
        return type instanceof PsiArrayType ? type : null;
    }
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkArrayInitializerApplicable(final PsiArrayInitializerExpression expression) = Hook.Result.nullToVoid(type(expression), null);
    
    @Hook
    private static Hook.Result getType(final PsiArrayInitializerExpressionImpl $this) {
        final Object type = type($this);
        return Hook.Result.nullToVoid(type instanceof PsiNewExpression expression ? expression.getType() : type);
    }
    
    @Hook(isStatic = true, value = LambdaUtil.class)
    private static Hook.Result getFunctionalInterfaceType(final PsiElement expression, final boolean tryToSubstitute) {
        if (expression.getParent() instanceof PsiArrayInitializerExpression initializer)
            if (type(initializer) instanceof PsiNewExpression newExpression) {
                final @Nullable PsiExpressionList expressionList = newExpression.getArgumentList();
                if (expressionList != null) {
                    final int lambdaIdx = LambdaUtil.getLambdaIdx(expressionList, expression);
                    if (lambdaIdx >= 0)
                        return { (Privilege) LambdaUtil.getSubstitutedType(expression, tryToSubstitute, lambdaIdx, PsiDiamondType.getDiamondsAwareResolveResult(newExpression)) };
                }
            }
        return Hook.Result.VOID;
    }
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    private static Hook.Result checkReturnStatementType(final PsiReturnStatement statement, final PsiElement parent) {
        if (parent instanceof PsiMethodImpl) {
            final @Nullable PsiElement eq = PsiTreeUtil.findSiblingBackward(parent.getLastChild(), EQ, null);
            if (eq != null) {
                final PsiTypeElement typeElement = ((PsiMethodImpl) parent).getReturnTypeElement();
                final @Nullable PsiType returnType = ((PsiMethodImpl) parent).getReturnType();
                if (typeElement == null || returnType == null || PsiTypes.voidType().equals(returnType) || SelfHandler.isSelfReference(typeElement.getText()))
                    return Hook.Result.NULL;
            }
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result isAvailable(final ImplementAbstractClassMethodsFix $this, final Project project, final PsiFile file, final PsiElement startElement, final PsiElement endElement)
            = Hook.Result.falseToVoid(startElement instanceof PsiArrayInitializerBackNewExpression, false);
    
    @Hook
    private static Hook.Result getFirstBodyElement(final PsiCodeBlockImpl $this) {
        final @Nullable PsiJavaToken lBrace = $this.getLBrace();
        return { lBrace != null ? lBrace : $this.getFirstChild() };
    }
    
    @Hook
    private static Hook.Result getLastBodyElement(final PsiCodeBlockImpl $this) {
        final @Nullable PsiJavaToken rBrace = $this.getRBrace();
        return { rBrace != null ? rBrace : $this.getFirstChild() };
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
        final IElementType tokenType = builder.getTokenType();
        if (tokenType == EQ) {
            builder.advanceLexer();
            final PsiBuilder.Marker block = builder.mark(), statement = builder.mark();
            final @Nullable PsiBuilder.Marker expression = ((Privilege) $this.myParser).getExpressionParser().parse(builder);
            if (expression == null)
                error(builder, JavaErrorBundle.message("expected.expression"));
            done(statement, JavaElementType.RETURN_STATEMENT);
            done(block, JavaElementType.CODE_BLOCK);
            semicolon(builder);
            done(declaration, anno ? JavaElementType.ANNOTATION_METHOD : JavaElementType.METHOD);
            return { declaration };
        }
        return Hook.Result.VOID;
    }
    
    @Hook(value = BlockSupportImpl.class, isStatic = true)
    private static Hook.Result tryReparseNode(final IReparseableElementTypeBase reparseable, final ASTNode node, final CharSequence newTextStr, final PsiManager manager, final Language baseLanguage, final CharTable charTable) {
        if (node instanceof PsiCodeBlockImpl codeBlock && PsiTreeUtil.skipWhitespacesAndCommentsBackward(codeBlock) instanceof PsiJavaToken token && token.getTokenType() == EQ) {
            final ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(JavaLanguage.INSTANCE);
            final PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(definition, definition.createLexer(codeBlock.getProject()), newTextStr);
            setLanguageLevel(builder, PsiUtil.getLanguageLevel(codeBlock));
            final PsiBuilder.Marker block = builder.mark(), statement = builder.mark();
            final PsiBuilder.Marker expr = JavaParser.INSTANCE.getExpressionParser().parse(builder);
            if (expr == null)
                error(builder, JavaErrorBundle.message("expected.expression"));
            done(statement, JavaElementType.RETURN_STATEMENT);
            while (!builder.eof())
                builder.advanceLexer();
            done(block, JavaElementType.CODE_BLOCK);
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
                    if (expression != null) {
                        final PsiExpression copy = (PsiExpression) expression.copy();
                        final LeafPsiElement eq = { EQ, "=" }, white = { WHITE_SPACE, " " };
                        final CompositeElement parent = method.getNode();
                        myParent(eq, parent);
                        myParent(white, parent);
                        markTree(eq, white);
                        markTree(white, (TreeElement) copy.getNode());
                        final PsiElement prev = body.getPrevSibling().getPrevSibling();
                        body.delete();
                        method.addRangeAfter(eq, copy, prev);
                    }
                }
            }
        }
        
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
            if (element instanceof PsiNewExpressionImpl expression && element.getClass() != PsiArrayInitializerBackNewExpression.class) {
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
                } else {
                    final @Nullable PsiVariable variable = parent instanceof PsiLocalVariable || parent instanceof PsiField ? (PsiVariable) parent : DefaultValueHandler.defaultVariable(expression);
                    if (variable != null && !(variable?.getTypeElement()?.isInferredType() ?? false))
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
