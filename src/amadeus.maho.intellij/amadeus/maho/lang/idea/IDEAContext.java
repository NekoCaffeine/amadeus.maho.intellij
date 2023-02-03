package amadeus.maho.lang.idea;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.lexer._JavaLexer;
import com.intellij.lang.java.parser.DeclarationParser;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.StatementParser;
import com.intellij.lexer.FlexLexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.Bottom;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiCapturedWildcardType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDiamondType;
import com.intellij.psi.PsiDisjunctionType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeVariable;
import com.intellij.psi.PsiTypeVisitor;
import com.intellij.psi.PsiTypeVisitorEx;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.java.JavaSpacePropertyProcessor;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LazyParseableElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FileBasedIndexEx;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.idea.handler.base.PsiClassesException;
import amadeus.maho.lang.idea.light.LightModifierList;
import amadeus.maho.lang.inspection.Callback;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.inspection.TestOnly;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Proxy;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.control.LinkedIterator;

import static amadeus.maho.util.bytecode.Bytecodes.INVOKEVIRTUAL;
import static com.intellij.psi.JavaTokenType.*;
import static com.intellij.psi.PsiModifier.*;
import static org.objectweb.asm.Opcodes.*;

@TransformProvider
public class IDEAContext {
    
    @TransformProvider
    public static class AdditionalOperators {
        
        public static final IJavaElementType
                NULL_OR     = { "NULL_OR" },
                SAFE_ACCESS = { "SAFE_ACCESS" };
        
        public static final TokenSet YIELD_EXPR_INDICATOR_TOKENS = TokenSet.create(NULL_OR, SAFE_ACCESS);
        
        @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
        private static IElementType advance(final IElementType capture, final @InvisibleType("com.intellij.lang.java.lexer._JavaLexer") FlexLexer $this) = capture == QUEST ? tryMapToken($this) : capture;
        
        @Privilege
        private static IElementType tryMapToken(final @InvisibleType("com.intellij.lang.java.lexer._JavaLexer") FlexLexer flexLexer) {
            final _JavaLexer lexer = (_JavaLexer) flexLexer;
            final int mark = lexer.zzMarkedPos, next = next(flexLexer);
            if (next == '.')
                return SAFE_ACCESS;
            if (next == '?')
                return NULL_OR;
            lexer.goTo(mark);
            return QUEST;
        }
        
        @Privilege
        @SneakyThrows
        private static int next(final @InvisibleType("com.intellij.lang.java.lexer._JavaLexer") FlexLexer flexLexer) {
            final _JavaLexer lexer = (_JavaLexer) flexLexer;
            final int zzInput;
            final int zzMarkedPosL = lexer.zzMarkedPos;
            int zzCurrentPosL = lexer.zzCurrentPos = lexer.zzStartRead = zzMarkedPosL;
            final int zzEndReadL = lexer.zzEndRead;
            CharSequence zzBufferL = lexer.zzBuffer;
            if (zzCurrentPosL < zzEndReadL) {
                zzInput = Character.codePointAt(zzBufferL, zzCurrentPosL);
                zzCurrentPosL += Character.charCount(zzInput);
            } else if (lexer.zzAtEOF)
                zzInput = _JavaLexer.YYEOF;
            else {
                final boolean eof = lexer.zzRefill();
                zzCurrentPosL = lexer.zzCurrentPos;
                zzBufferL = lexer.zzBuffer;
                if (eof)
                    zzInput = _JavaLexer.YYEOF;
                else {
                    zzInput = Character.codePointAt(zzBufferL, zzCurrentPosL);
                    zzCurrentPosL += Character.charCount(zzInput);
                }
            }
            lexer.zzMarkedPos = zzCurrentPosL;
            if (zzInput == _JavaLexer.YYEOF && lexer.zzStartRead == lexer.zzCurrentPos)
                lexer.zzAtEOF = true;
            return zzInput;
        }
        
        @Hook(value = StatementParser.class, isStatic = true, at = @At(method = @At.MethodInsn(name = "contains")), capture = true)
        private static Hook.Result isStmtYieldToken(final IElementType capture, final PsiBuilder builder, final IElementType tokenType) = Hook.Result.falseToVoid(YIELD_EXPR_INDICATOR_TOKENS.contains(capture), false);
        
    }
    
    public static class OperatorData {
        
        public static final HashMap<String, String>       operatorSymbol2operatorName = { };
        public static final HashMap<String, String>       operatorName2operatorSymbol = { };
        public static final HashMap<String, IElementType> operatorName2operatorType   = { };
        public static final HashMap<IElementType, String> operatorType2operatorName   = { };
        public static final HashMap<IElementType, String> operatorType2operatorSymbol = { };
        
        static {
            // @formatter:off
            add("PLUS",     PLUS,        "+"   );
            add("MINUS",    MINUS,       "-"   );
            add("NOT",      EXCL,        "!"   );
            add("TILDE",    TILDE,       "~"   );
            add("POSTINC",  PLUSPLUS,   "_++"  );
            add("POSTDEC",  MINUSMINUS, "_--"  );
            add("PREINC",   PLUSPLUS,    "++_" );
            add("PREDEC",   MINUSMINUS,  "--_" );
            add("OROR",     OROR,        "||"  );
            add("ANDAND",   ANDAND,      "&&"  );
            add("OR",       OR,          "|"   );
            add("XOR",      XOR,         "^"   );
            add("AND",      AND,         "&"   );
            add("EQ",       EQEQ,        "=="  );
            add("NE",       NE,          "!="  );
            add("LT",       LT,          "<"   );
            add("GT",       GT,          ">"   );
            add("LE",       LE,          "<="  );
            add("GE",       GE,          ">="  );
            add("LTLT",     LTLT,        "<<"  );
            add("GTGT",     GTGT,        ">>"  );
            add("GTGTGT",   GTGTGT,      ">>>" );
            add("PLUS",     PLUS,        "+"   );
            add("MINUS",    MINUS,       "-"   );
            add("MUL",      ASTERISK,    "*"   );
            add("DIV",      DIV,         "/"   );
            add("MOD",      PERC,        "%"   );
            add("OREQ",     OREQ,        "|="  );
            add("XOREQ",    XOREQ,       "^="  );
            add("ANDEQ",    ANDEQ,       "&="  );
            add("LTLTEQ",   LTLTEQ,      "<<=" );
            add("GTGTEQ",   GTGTEQ,      ">>=" );
            add("GTGTGTEQ", GTGTGTEQ,    ">>>=");
            add("PLUSEQ",   PLUSEQ,      "+="  );
            add("MINUSEQ",  MINUSEQ,     "-="  );
            add("MULEQ",    ASTERISKEQ,  "*="  );
            add("DIVEQ",    DIVEQ,       "/="  );
            add("MODEQ",    PERCEQ,      "%="  );
            // @formatter:on
        }
        
        public static void add(final String name, final IElementType type, final String symbol) {
            operatorSymbol2operatorName[symbol] = name;
            operatorName2operatorSymbol[name] = symbol;
            operatorName2operatorType[name] = type;
            operatorType2operatorName[type] = name;
            operatorType2operatorSymbol[type] = symbol;
        }
        
    }
    
    public static class TypeMapper extends PsiTypeVisitorEx<PsiType> {
        
        public final Map<PsiType, PsiType> mapping;
        
        public TypeMapper(final Map<PsiType, PsiType> mapping) = this.mapping = mapping;
        
        public PsiType mapType(final PsiType type) = type.accept(this);
        
        @Override
        public PsiType visitArrayType(final PsiArrayType type) {
            final PsiType componentType = type.getComponentType();
            final PsiType mappedComponent = mapType(componentType);
            if (mappedComponent == componentType)
                return type;
            return new PsiArrayType(mappedComponent, type.getAnnotationProvider());
        }
        
        @Override
        public PsiType visitEllipsisType(final PsiEllipsisType type) {
            final PsiType componentType = type.getComponentType();
            final PsiType mappedComponent = mapType(componentType);
            if (mappedComponent == componentType)
                return type;
            return new PsiEllipsisType(mappedComponent, type.getAnnotationProvider());
        }
        
        @Override
        public PsiType visitTypeVariable(final PsiTypeVariable var) = var;
        
        @Override
        public PsiType visitBottom(final Bottom bottom) = bottom;
        
        @Override
        public PsiType visitCapturedWildcardType(final PsiCapturedWildcardType type) = type;
        
        @Override
        public PsiType visitPrimitiveType(final PsiPrimitiveType primitiveType) = primitiveType;
        
        @Override
        public PsiType visitType(final PsiType type) = type;
        
        @Override
        public PsiType visitWildcardType(final PsiWildcardType wildcardType) {
            final @Nullable PsiType bound = wildcardType.getBound();
            final PsiManager manager = wildcardType.getManager();
            if (bound == null)
                return PsiWildcardType.createUnbounded(manager);
            final PsiType newBound = mapType(bound);
            return newBound == bound ? wildcardType : wildcardType.isExtends() ? PsiWildcardType.createExtends(manager, newBound) : PsiWildcardType.createSuper(manager, newBound);
        }
        
        @Override
        public PsiType visitIntersectionType(final PsiIntersectionType intersectionType) {
            final List<PsiType> substituted = new SmartList<>();
            boolean flag = false;
            for (final PsiType component : intersectionType.getConjuncts()) {
                final PsiType mapped = mapType(component);
                flag |= mapped != component;
                substituted.add(mapped);
            }
            return flag ? PsiIntersectionType.createIntersection(false, substituted.toArray(PsiType.EMPTY_ARRAY)) : intersectionType;
        }
        
        @Override
        public PsiType visitDisjunctionType(final PsiDisjunctionType disjunctionType) {
            final List<PsiType> substituted = new SmartList<>();
            boolean flag = false;
            for (final PsiType component : disjunctionType.getDisjunctions()) {
                final PsiType mapped = mapType(component);
                flag |= mapped != component;
                substituted.add(mapped);
            }
            return flag ? disjunctionType.newDisjunctionType(substituted) : disjunctionType;
        }
        
        @Override
        public PsiType visitDiamondType(final PsiDiamondType diamondType) = diamondType;
        
        @Override
        public PsiType visitClassType(final PsiClassType type) {
            final PsiType result = mapping.getOrDefault(type, type);
            if (result != type || ((PsiClassType) result).getParameters().length == 0)
                return result;
            final PsiClassType.ClassResolveResult classResolveResult = type.resolveGenerics();
            final PsiClass psiClass = classResolveResult.getElement();
            if (psiClass == null)
                return type;
            PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
            for (final Map.Entry<PsiTypeParameter, PsiType> entry : classResolveResult.getSubstitutor().getSubstitutionMap().entrySet()) {
                final PsiType value = entry.getValue();
                substitutor = substitutor.put(entry.getKey(), value == null ? null : mapType(value));
            }
            return new PsiImmediateClassType(psiClass, substitutor);
        }
        
    }
    
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class TypeParameterSearcher extends PsiTypeVisitor<Set<PsiTypeParameter>> {
        
        HashSet<PsiClassType> visited = { };
        
        HashSet<PsiTypeParameter> result = { };
        
        @Override
        public Set<PsiTypeParameter> visitType(final PsiType type) = result;
        
        @Override
        public Set<PsiTypeParameter> visitArrayType(final PsiArrayType arrayType) = arrayType.getComponentType().accept(this);
        
        @Override
        public Set<PsiTypeParameter> visitClassType(final PsiClassType classType) {
            if (visited.add(classType)) {
                final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
                final PsiClass element = resolveResult.getElement();
                if (element instanceof PsiTypeParameter) {
                    result.add((PsiTypeParameter) element);
                    Stream.of(element.getExtendsListTypes()).forEach(type -> type.accept(this));
                }
                if (element != null) {
                    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
                    for (final PsiTypeParameter parameter : PsiUtil.typeParametersIterable(element)) {
                        final PsiType psiType = substitutor.substitute(parameter);
                        if (psiType != null)
                            psiType.accept(this);
                    }
                }
            }
            return result;
        }
        
        @Override
        public Set<PsiTypeParameter> visitWildcardType(final PsiWildcardType wildcardType) {
            final PsiType bound = wildcardType.getBound();
            if (bound != null)
                bound.accept(this);
            return result;
        }
        
    }
    
    @Hook(value = HighlightUtil.class, isStatic = true)
    static Hook.Result checkUnderscore(final PsiIdentifier identifier, final LanguageLevel languageLevel) {
        if ("_".equals(identifier.getText()) && !(identifier.getParent() instanceof PsiParameter parameter && parameter.getNameIdentifier() == identifier &&
                                                  parameter.getParent() instanceof PsiParameterList parameterList && parameterList.getParent() instanceof PsiLambdaExpression))
            return { HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier).descriptionAndTooltip(JavaErrorBundle.message("underscore.identifier.error")) };
        return Hook.Result.NULL;
    }
    
    @Proxy(PUTFIELD)
    public static native void myParsed(LazyParseableElement $this, boolean value);
    
    @Proxy(PUTFIELD)
    public static native void myText(LazyParseableElement $this, Getter<String> value);
    
    @Proxy(GETFIELD)
    public static native JavaParser myParser(DeclarationParser $this);
    
    @Proxy(GETFIELD)
    public static native HighlightInfoHolder myHolder(HighlightVisitorImpl $this);
    
    @Proxy(GETFIELD)
    public static native TreeElement firstChild(CompositeElement $this);
    
    @Proxy(PUTFIELD)
    public static native void firstChild(CompositeElement $this, @Nullable TreeElement value);
    
    @Proxy(GETFIELD)
    public static native TreeElement lastChild(CompositeElement $this);
    
    @Proxy(PUTFIELD)
    public static native void lastChild(CompositeElement $this, @Nullable TreeElement value);
    
    @Proxy(GETFIELD)
    public static native TreeElement myNextSibling(TreeElement $this);
    
    @Proxy(PUTFIELD)
    public static native void myNextSibling(TreeElement $this, @Nullable TreeElement value);
    
    @Proxy(GETFIELD)
    public static native TreeElement myPrevSibling(TreeElement $this);
    
    @Proxy(PUTFIELD)
    public static native void myPrevSibling(TreeElement $this, @Nullable TreeElement value);
    
    @Proxy(GETFIELD)
    public static native CompositeElement myParent(TreeElement $this);
    
    @Proxy(PUTFIELD)
    public static native void myParent(TreeElement $this, @Nullable CompositeElement value);
    
    @Proxy(GETFIELD)
    public static native ASTNode myChild1(JavaSpacePropertyProcessor $this);
    
    @Proxy(GETFIELD)
    public static native ASTNode myChild2(JavaSpacePropertyProcessor $this);
    
    @Proxy(GETFIELD)
    public static native int myRole1(JavaSpacePropertyProcessor $this);
    
    @Proxy(GETFIELD)
    public static native int myRole2(JavaSpacePropertyProcessor $this);
    
    @Proxy(GETFIELD)
    public static native IElementType myType1(JavaSpacePropertyProcessor $this);
    
    @Proxy(GETFIELD)
    public static native IElementType myType2(JavaSpacePropertyProcessor $this);
    
    @Proxy(GETFIELD)
    public static native CommonCodeStyleSettings mySettings(JavaSpacePropertyProcessor $this);
    
    @Proxy(INVOKEVIRTUAL)
    public static native void createSpaceInCode(JavaSpacePropertyProcessor $this, boolean space);
    
    @Proxy(INVOKEVIRTUAL)
    public static native void createSpaceWithLinefeedIfListWrapped(JavaSpacePropertyProcessor $this, PsiExpressionList list, boolean space);
    
    @Proxy(PUTFIELD)
    public static native void myResult(JavaSpacePropertyProcessor $this, Spacing value);
    
    public static void markTree(final @Nullable TreeElement prev, final @Nullable TreeElement next) {
        if (prev != null)
            myNextSibling(prev, next);
        if (next != null)
            myPrevSibling(next, prev);
    }
    
    public static void markEquivalent(final CompositeElement self, final CompositeElement target) {
        myParent(self, myParent(target));
        myPrevSibling(self, myPrevSibling(target));
        myNextSibling(self, myNextSibling(target));
    }
    
    public static void markChild(final CompositeElement holder, final CompositeElement target) = markChild(holder, firstChild(target), lastChild(target));
    
    public static void markChild(final CompositeElement holder, final TreeElement first, final TreeElement last) {
        firstChild(holder, first);
        lastChild(holder, last);
        myPrevSibling(first, null);
        myNextSibling(last, null);
        new LinkedIterator<>(TreeElement::getTreeNext, first).stream(true).forEach(it -> myParent(it, holder));
    }
    
    public static void markChildAs(final CompositeElement holder, final TreeElement target) {
        firstChild(holder, target);
        lastChild(holder, target);
        myPrevSibling(target, null);
        myNextSibling(target, null);
        myParent(target, holder);
    }
    
    public static void markChildAs(final CompositeElement holder, final TreeElement... targets) {
        if (targets.length > 0) {
            firstChild(holder, targets[0]);
            lastChild(holder, targets[targets.length - 1]);
            @Nullable TreeElement l = null, r = null;
            for (final TreeElement target : targets) {
                r = target;
                markTree(l, r);
                myParent(r, holder);
                l = r;
            }
            myNextSibling(r, null);
        }
    }
    
    public static void replaceMarkChild(final CompositeElement holder, final TreeElement target, final TreeElement element) {
        final TreeElement prev = target.getTreePrev(), next = target.getTreeNext();
        markTree(prev, element);
        markTree(element, next);
        if (prev == null)
            firstChild(holder, element);
        if (next == null)
            lastChild(holder, element);
        myParent(element, holder);
    }
    
    public static String atomicReferenceName(final PsiType type) = (type instanceof PsiPrimitiveType primitiveType ?
            switch (primitiveType.getKind().getName()) {
                case "boolean" -> AtomicBoolean.class;
                case "int"     -> AtomicInteger.class;
                case "long"    -> AtomicLong.class;
                default        -> AtomicReference.class;
            } : AtomicReference.class).getCanonicalName();
    
    public static PsiClassType typeWithGenerics(final PsiClass psiClass) {
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiClass.getProject());
        final PsiType psiTypes[] = Stream.of(psiClass.getTypeParameters()).map(factory::createType).toArray(PsiType.ARRAY_FACTORY::create);
        if (psiTypes.length > 0)
            return factory.createType(psiClass, psiTypes);
        else
            return factory.createType(psiClass);
    }
    
    public static List<PsiClass> supers(final PsiClass psiClass) = CachedValuesManager.getProjectPsiDependentCache(psiClass, it -> Stream.concat(Stream.of(it),
            computeReadActionIgnoreDumbMode(() -> InheritanceUtil.getSuperClasses(it)).stream()).collect(Collectors.toList()));
    
    public static Collection<PsiField> fields(final PsiClass classTree) = classTree instanceof PsiExtensibleClass extensibleClass ? extensibleClass.getOwnFields() : filterMembers(classTree, PsiField.class);
    
    public static Collection<PsiMethod> methods(final PsiClass classTree) = classTree instanceof PsiExtensibleClass extensibleClass ? extensibleClass.getOwnMethods() : filterMembers(classTree, PsiMethod.class);
    
    public static Collection<PsiClass> innerClasses(final PsiClass classTree) = classTree instanceof PsiExtensibleClass extensibleClass ? extensibleClass.getOwnInnerClasses() : filterMembers(classTree, PsiClass.class);
    
    public static Collection<PsiMember> members(final PsiClass classTree)
            = Stream.of(classTree.getChildren()).filter(element -> element instanceof PsiField || element instanceof PsiMethod).map(PsiMember.class::cast).collect(Collectors.toList());
    
    public static <T extends PsiElement> Collection<T> filterMembers(final PsiClass classTree, final Class<T> desiredClass)
            = Stream.of(classTree.getChildren()).filter(desiredClass::isInstance).map(desiredClass::cast).collect(Collectors.toList());
    
    public static void followStatic(final PsiModifierListOwner target, final LightModifierList follower) {
        if (target.hasModifierProperty(STATIC))
            follower.addModifier(STATIC);
    }
    
    public static void followModifier(final PsiModifierListOwner target, final LightModifierList follower) = follower.copyModifiers(target.getModifierList());
    
    public static void followAccess(final PsiModifierListOwner target, final LightModifierList follower) {
        final @Nullable PsiModifierList modifierList = target.getModifierList();
        if (modifierList != null)
            Stream.of(PUBLIC, PROTECTED, PACKAGE_LOCAL, PRIVATE, STATIC).filter(modifierList::hasModifierProperty).forEach(follower::addModifier);
    }
    
    public static void followAnnotation(final PsiAnnotation target, final String on, final LightModifierList follower) = Optional.ofNullable(target.findAttributeValue(on)).ifPresent(value -> {
        if (value instanceof PsiAnnotation annotation)
            follower.addAnnotation(annotation);
        else if (value instanceof PsiArrayInitializerExpression expression)
            Stream.of(expression.getInitializers())
                    .filter(PsiAnnotation.class::isInstance)
                    .map(PsiAnnotation.class::cast)
                    .forEach(follower::addAnnotation);
    });
    
    public static PsiAnnotation copy(final PsiAnnotation annotation) = JavaPsiFacade.getElementFactory(annotation.getProject()).createAnnotationFromText(annotation.getText(), annotation.getParent());
    
    public static void followAnnotation(final @Nullable PsiModifierList target, final LightModifierList follower) {
        if (target != null) {
            Stream.of(target.getAnnotations())
                    .filter(IDEAContext::shouldFollowAnnotation)
                    .forEach(follower::addAnnotation);
        }
    }
    
    public static void followAnnotationWithoutNullable(final @Nullable PsiModifierList target, final LightModifierList follower) {
        if (target != null) {
            Stream.of(target.getAnnotations())
                    .filter(annotation -> shouldFollowAnnotation(annotation) && !Nullable.class.getCanonicalName().equals(annotation.getQualifiedName()))
                    .forEach(follower::addAnnotation);
        }
    }
    
    public static void followNullable(final @Nullable PsiModifierList target, final LightModifierList follower) {
        if (target != null) {
            Stream.of(target.getAnnotations())
                    .filter(annotation -> Nullable.class.getCanonicalName().equals(annotation.getQualifiedName()))
                    .forEach(follower::addAnnotation);
        }
    }
    
    public static final HashSet<Class<? extends Annotation>> followableAnnotationTypes = { List.of(Deprecated.class, Nullable.class, Callback.class, TestOnly.class) };
    
    public static boolean shouldFollowAnnotation(@Nullable final String name) = name != null && followableAnnotationTypes.stream().map(Class::getCanonicalName).anyMatch(name::equals);
    
    public static boolean shouldFollowAnnotation(final PsiAnnotation annotation) = shouldFollowAnnotation(annotation.getQualifiedName());
    
    public static Function<String, String> simplify(final String context) {
        final HashSet<String> mark = { };
        return name -> mark.add(name) ? name : context + "$" + name;
    }
    
    public static boolean marked(final PsiAnnotation annotation, final String mark) = annotation.resolveAnnotationType()?.hasAnnotation(mark) ?? false;
    
    public static void runReadActionIgnoreDumbMode(final Runnable runnable) {
        if (((Privilege) FileBasedIndexEx.ourDumbModeAccessTypeStack).get().contains(DumbModeAccessType.RELIABLE_DATA_ONLY))
            if (ApplicationManager.getApplication().isReadAccessAllowed())
                runnable.run();
            else
                ReadAction.run(runnable::run);
        else if (ApplicationManager.getApplication().isReadAccessAllowed())
            DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(runnable);
        else
            ReadAction.run(() -> DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(runnable));
    }
    
    public static <T, E extends Throwable> T computeReadActionIgnoreDumbMode(final ThrowableComputable<T, E> computable) throws E {
        if (((Privilege) FileBasedIndexEx.ourDumbModeAccessTypeStack).get().contains(DumbModeAccessType.RELIABLE_DATA_ONLY))
            if (ApplicationManager.getApplication().isReadAccessAllowed())
                return computable.compute();
            else
                return ReadAction.compute(computable);
        else if (ApplicationManager.getApplication().isReadAccessAllowed())
            return DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(computable);
        else
            return ReadAction.compute(() -> DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(computable));
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static String getFullProductName(final String capture, final ApplicationNamesInfo $this) = capture + " with Maho";
    
    public static <A extends Annotation> @Nullable PsiClassType accessPsiClass(final A annotation, final Function<A, Class<?>> accessor) {
        try {
            accessor.apply(annotation);
        } catch (final PsiClassesException e) {
            if (e.classes().size() > 0)
                return e.classes()[0];
        }
        return null;
    }
    
    public static <A extends Annotation> List<? extends PsiClassType> accessPsiClasses(final A annotation, final Function<A, Class<?>[]> accessor) {
        try {
            accessor.apply(annotation);
        } catch (final PsiClassesException e) {
            return e.classes();
        }
        return List.of();
    }
    
}
