package amadeus.maho.lang.idea.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.project.ProjectKt;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;

import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.ResourceAgent;
import amadeus.maho.lang.ResourceBundle;
import amadeus.maho.lang.idea.IDEAContext;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.ExtensibleMembers;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.handler.base.HandlerMarker;
import amadeus.maho.lang.idea.light.LightField;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.control.LinkedIterator;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.tuple.Tuple;
import amadeus.maho.util.tuple.Tuple2;
import amadeus.maho.util.tuple.Tuple3;

import static amadeus.maho.lang.javac.handler.ResourceBundleHandler.PRIORITY;

@Handler(value = ResourceBundle.class, priority = PRIORITY)
public class ResourceBundleHandler extends BaseHandler<ResourceBundle> {
    
    public static final int PRIORITY = -1 << 12;
    
    @Override
    public void processClass(final PsiClass tree, final ResourceBundle annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) {
        if (tree != context)
            return;
        try {
            final Path location = location(tree, annotation);
            final FieldDefaultsHandler fieldDefaultsHandler = HandlerMarker.Marker.baseHandlers().stream().cast(FieldDefaultsHandler.class).findFirst().orElseThrow();
            final PsiAnnotation fieldDefaultAnnotationTree = JavaPsiFacade.getElementFactory(tree.getProject()).createAnnotationFromText("@%s".formatted(FieldDefaults.class.getCanonicalName()), context);
            final HashMap<String, Tuple2<PsiMethod, ResourceAgent>> agents = { };
            final boolean itf = tree.isInterface();
            Stream.concat(new LinkedIterator<>(PsiClass::getSuperClass, tree).stream(true), supers(tree).stream().filter(PsiClass::isInterface))
                    .map(IDEAContext::methods)
                    .flatMap(Collection::stream)
                    .filter(method -> method.getReturnType() != null)
                    .forEach(method -> {
                        if (!itf || method.hasModifierProperty(PsiModifier.STATIC)) {
                            final PsiParameterList list = method.getParameterList();
                            if (list.getParametersCount() == 1 && list.getParameter(0).getType() instanceof PsiClassType classType) {
                                final @Nullable PsiClass resolve = classType.resolve();
                                if (resolve != null && resolve.getQualifiedName()?.equals(String.class.getCanonicalName()) ?? false) {
                                    final List<Tuple2<ResourceAgent, PsiAnnotation>> annotations = HandlerMarker.EntryPoint.getAnnotationsByType(method, ResourceAgent.class);
                                    if (annotations.size() == 1) {
                                        final ResourceAgent agentAnnotation = annotations[0].v1;
                                        agents.computeIfAbsent(agentAnnotation.value(), _ -> Tuple.tuple(method, agentAnnotation));
                                    }
                                }
                            }
                        }
                    });
            final List<Tuple3<Pattern, PsiMethod, ResourceAgent>> patterns = agents.entrySet().stream()
                    .map(entry -> {
                        try { return Tuple.tuple(Pattern.compile(entry.getKey()), entry.getValue().v1, entry.getValue().v2); } catch (final PatternSyntaxException e) { return null; }
                    })
                    .nonnull()
                    .toList();
            Files.walk(location, annotation.visitOptions()).forEach(path -> {
                final String arg = location % path | "/";
                final Set<PsiMethod> symbols = new HashSet<>();
                patterns.stream()
                        .filter(tuple -> shouldHandle(path, tuple.v3))
                        .map(tuple -> Tuple.tuple(tuple.v1.matcher(arg), tuple.v2))
                        .filter(tuple -> tuple.v1.find())
                        .forEach(tuple -> {
                            if (symbols.isEmpty()) {
                                final LightField mark = { tree, name(tuple.v1, location, path), resourceType(path, tuple.v2), annotationTree };
                                if (tuple.v2.hasModifierProperty(PsiModifier.STATIC))
                                    mark.addModifier(PsiModifier.STATIC);
                                mark.setNavigationElement(tree);
                                mark.setContainingClass(tree);
                                fieldDefaultsHandler.processVariable(mark, annotation.fieldDefaults(), fieldDefaultAnnotationTree, members, context);
                                members.inject(mark);
                            }
                            symbols += tuple.v2;
                        });
            });
        } catch (final IOException ignored) { }
    }
    
    protected boolean shouldHandle(final Path path, final ResourceAgent agentAnnotation) = ArrayHelper.contains(agentAnnotation.types(), Files.isDirectory(path) ? ResourceAgent.Type.DIRECTORY : ResourceAgent.Type.FILE);
    
    protected String name(final Matcher matcher, final Path location, final Path path) {
        final Map<String, Integer> map = matcher.pattern().namedGroupsIndex();
        final @Nullable String group = map.containsKey("name") ? matcher.group("name") : null, name = group ?? defaultName(location, path);
        final int p_index[] = { -1 };
        return name.codePoints().map(c -> (++p_index[0] == 0 ? Character.isJavaIdentifierStart(c) : Character.isJavaIdentifierPart(c)) ? c : '_').collectCodepoints();
    }
    
    protected String defaultName(final Path location, final Path path) {
        final String parent = (location % path.getParent()).toString();
        return parent.isEmpty() ? path.fileName() : parent + path.getFileSystem().getSeparator() + path.fileName();
    }
    
    protected PsiType resourceType(final Path path, final PsiMethod method) = method.getReturnType();
    
    @Override
    public void check(final PsiElement tree, final ResourceBundle annotation, final PsiAnnotation annotationTree, final ProblemsHolder holder, final QuickFixFactory quickFix) {
        if (tree instanceof PsiClass context) {
            try {
                final Path location = location(tree, annotation);
                if (Files.isDirectory(location)) {
                    final HashMap<String, Tuple2<PsiMethod, ResourceAgent>> agents = { };
                    final boolean itf = context.isInterface();
                    supers(context).stream()
                            .map(IDEAContext::methods)
                            .flatMap(Collection::stream)
                            .filter(method -> method.getReturnType() != null)
                            .forEach(method -> {
                                if (!itf || method.hasModifierProperty(PsiModifier.STATIC)) {
                                    final PsiParameterList list = method.getParameterList();
                                    if (list.getParametersCount() == 1) {
                                        final @Nullable PsiParameter parameter = list.getParameter(0);
                                        if (parameter != null && parameter.getType() instanceof PsiClassType classType) {
                                            final @Nullable PsiClass resolve = classType.resolve();
                                            if (resolve != null) {
                                                final @Nullable String qualifiedName = resolve.getQualifiedName();
                                                if (String.class.getCanonicalName().equals(qualifiedName)) {
                                                    final List<Tuple2<ResourceAgent, PsiAnnotation>> annotations = HandlerMarker.EntryPoint.getAnnotationsByType(method, ResourceAgent.class);
                                                    if (annotations.size() == 1) {
                                                        final ResourceAgent agentAnnotation = annotations[0].v1;
                                                        agents.compute(agentAnnotation.value(), (regex, tuple) -> {
                                                            if (tuple == null)
                                                                return Tuple.tuple(method, agentAnnotation);
                                                            holder.registerProblem(annotationTree, "Unexpected duplication of the path regular expression for the method in which the resource agent is located.\n  %s\n  %s"
                                                                    .formatted(tuple.v1, method), ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(tuple.v1), quickFix.createDeleteFix(method));
                                                            return tuple;
                                                        });
                                                    }
                                                }
                                            }
                                            
                                        }
                                    }
                                }
                            });
                    final List<Tuple3<Pattern, PsiMethod, ResourceAgent>> patterns = agents.entrySet().stream()
                            .map(entry -> {
                                try { return Tuple.tuple(Pattern.compile(entry.getKey()), entry.getValue().v1, entry.getValue().v2); } catch (final PatternSyntaxException e) { return null; }
                            })
                            .nonnull()
                            .toList();
                    Files.walk(location, annotation.visitOptions()).forEach(path -> {
                        final String arg = location % path | "/";
                        final Set<PsiMethod> symbols = new HashSet<>();
                        patterns.stream()
                                .filter(tuple -> shouldHandle(path, tuple.v3))
                                .map(tuple -> Tuple.tuple(tuple.v1.matcher(arg), tuple.v2))
                                .filter(tuple -> tuple.v1.find())
                                .forEach(tuple -> symbols += tuple.v2);
                        if (symbols.size() > 1)
                            holder.registerProblem(annotationTree, "The same path is repeatedly matched by multiple regular expressions.\npath: %s\n  %s"
                                    .formatted(arg, symbols), ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
                    });
                }
            } catch (final IOException e) {
                holder.registerProblem(annotationTree, "An IO exception occurred when visiting the path corresponding to the ResourceBundle: %s"
                        .formatted(e.getMessage()), ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
            }
        }
    }
    
    protected Path location(final PsiElement context, final ResourceBundle annotation) {
        final String value = annotation.value();
        return value.length() > 0 && value.charAt(0) == '!' ? Path.of(value.substring(1)) : ProjectKt.getStateStore(context.getProject()).getProjectBasePath() / value;
    }
    
}
