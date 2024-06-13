package amadeus.maho.lang.idea.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.project.ProjectKt;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;

import amadeus.maho.lang.ResourceAgent;
import amadeus.maho.lang.ResourceBundle;
import amadeus.maho.lang.idea.IDEAContext;
import amadeus.maho.lang.idea.handler.base.BaseHandler;
import amadeus.maho.lang.idea.handler.base.ExtensibleMembers;
import amadeus.maho.lang.idea.handler.base.Handler;
import amadeus.maho.lang.idea.handler.base.HandlerSupport;
import amadeus.maho.lang.idea.light.LightField;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.control.LinkedIterator;
import amadeus.maho.util.function.FunctionHelper;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.tuple.Tuple2;

import static amadeus.maho.lang.idea.IDEAContext.supers;
import static amadeus.maho.lang.javac.handler.ResourceBundleHandler.PRIORITY;

@Handler(value = ResourceBundle.class, priority = PRIORITY)
public class ResourceBundleHandler extends BaseHandler<ResourceBundle> {
    
    public static final int PRIORITY = -1 << 12;
    
    public record AgentMethod(PsiMethod method, Pattern pattern, ResourceAgent agent) { }
    
    private List<PsiClass> parameter(final PsiMethod method) = Stream.of(method.getParameterList().getParameters())
            .map(PsiParameter::getType)
            .map(type -> type instanceof PsiClassType classType ? classType.resolve() : null)
            .toList();
    
    @Override
    public void processClass(final PsiClass tree, final ResourceBundle annotation, final PsiAnnotation annotationTree, final ExtensibleMembers members, final PsiClass context) {
        if (tree != context)
            return;
        try {
            final Path location = location(tree, annotation);
            final HashMap<String, AgentMethod> agents = { };
            final boolean itf = tree.isInterface();
            final @Nullable PsiClass stringClass = PsiElementFactory.getInstance(tree.getProject()).createTypeByFQClassName(String.class.getCanonicalName()).resolve();
            if (stringClass == null)
                return;
            final List<List<PsiClass>> agentParameters = List.of(List.of(stringClass), List.of(stringClass, stringClass));
            Stream.concat(LinkedIterator.of(PsiClass::getSuperClass, tree).stream(true), supers(tree).stream().filter(PsiClass::isInterface))
                    .map(IDEAContext::methods)
                    .flatMap(Collection::stream)
                    .filter(method -> method.getReturnType() != null)
                    .forEach(method -> {
                        if (!itf || method.hasModifierProperty(PsiModifier.STATIC))
                            if (agentParameters.contains(parameter(method))) {
                                final List<Tuple2<ResourceAgent, PsiAnnotation>> annotations = HandlerSupport.getAnnotationsByType(method, ResourceAgent.class);
                                if (annotations.size() == 1) {
                                    final ResourceAgent agentAnnotation = annotations[0].v1;
                                    final @Nullable Pattern pattern = ResourceAgentHandler.pattern(annotations[0].v2);
                                    if (pattern != null) {
                                        final Map<String, Integer> namedGroupsIndex = pattern.namedGroups();
                                        final List<String> missingKey = Stream.of(method.getParameterList().getParameters()).map(PsiParameter::getName).filterNot(namedGroupsIndex.keySet()::contains).toList();
                                        if (missingKey.isEmpty())
                                            agents.computeIfAbsent(agentAnnotation.value(), _ -> new AgentMethod(method, pattern, agentAnnotation));
                                        
                                    }
                                }
                            }
                    });
            Files.walk(location, annotation.visitOptions()).forEach(path -> {
                final String arg = location % path | "/";
                final Map<String, List<PsiMethod>> record = new HashMap<>();
                agents.values().stream()
                        .filter(agentMethod -> shouldHandle(path, agentMethod.agent()))
                        .forEach(agentMethod -> {
                            final Matcher matcher = agentMethod.pattern().matcher(arg);
                            if (matcher.find()) {
                                final PsiMethod method = agentMethod.method();
                                final String name = name(agentMethod.agent().format(), matcher, location, path);
                                final List<PsiMethod> methods = record.computeIfAbsent(name, FunctionHelper.abandon(LinkedList::new));
                                methods += method;
                                if (methods.size() > 1)
                                    return;
                                final LightField mark = { tree, name, resourceType(path, method), annotationTree };
                                if (members.shouldInject(mark)) {
                                    if (method.hasModifierProperty(PsiModifier.STATIC))
                                        mark.addModifier(PsiModifier.STATIC);
                                    mark.setNavigationElement(tree);
                                    mark.setContainingClass(tree);
                                    FieldDefaultsHandler.transformModifiers(mark.getModifierList(), annotation.fieldDefaults());
                                    members.inject(mark);
                                }
                            }
                        });
            });
        } catch (final IOException ignored) { }
    }
    
    protected boolean shouldHandle(final Path path, final ResourceAgent agentAnnotation) = ArrayHelper.contains(agentAnnotation.types(), Files.isDirectory(path) ? ResourceAgent.Type.DIRECTORY : ResourceAgent.Type.FILE);
    
    protected String name(final String format, final Matcher matcher, final Path location, final Path path) {
        final Map<String, Integer> map = matcher.pattern().namedGroups();
        final @Nullable String group = map.containsKey("name") ? matcher.group("name") : null, name = format.formatted(group ?? defaultName(location, path));
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
                    final HashMap<String, AgentMethod> agents = { };
                    final boolean itf = context.isInterface();
                    supers(context).stream()
                            .map(IDEAContext::methods)
                            .flatMap(Collection::stream)
                            .filter(method -> method.getReturnType() != null)
                            .forEach(method -> {
                                if (!itf || method.hasModifierProperty(PsiModifier.STATIC)) {
                                    if (Stream.of(method.getParameterList().getParameters()).map(PsiParameter::getType).allMatch(type ->
                                            type instanceof PsiClassType classType && String.class.getCanonicalName().equals(classType.resolve()?.getQualifiedName() ?? null))) {
                                        final List<Tuple2<ResourceAgent, PsiAnnotation>> annotations = HandlerSupport.getAnnotationsByType(method, ResourceAgent.class);
                                        if (annotations.size() == 1) {
                                            final ResourceAgent agentAnnotation = annotations[0].v1;
                                            final @Nullable Pattern pattern = ResourceAgentHandler.pattern(annotations[0].v2);
                                            if (pattern != null) {
                                                final Map<String, Integer> namedGroupsIndex = pattern.namedGroups();
                                                final List<String> missingKey = Stream.of(method.getParameterList().getParameters()).map(PsiParameter::getName).filterNot(namedGroupsIndex.keySet()::contains).toList();
                                                if (missingKey.isEmpty())
                                                    agents.compute(agentAnnotation.value(), (regex, agentMethod) -> {
                                                        if (agentMethod == null)
                                                            return new AgentMethod(method, pattern, agentAnnotation);
                                                        holder.registerProblem(annotationTree, STR."Unexpected duplication of the path regular expression for the method in which the resource agent is located.\n\t\{annotationTree}\n\t\{method}",
                                                                ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree), quickFix.createDeleteFix(method));
                                                        return agentMethod;
                                                    });
                                            }
                                        }
                                    }
                                }
                            });
                    Files.walk(location, annotation.visitOptions()).forEach(path -> {
                        final String arg = location % path | "/";
                        final Map<String, List<PsiMethod>> record = new HashMap<>();
                        agents.values().stream()
                                .filter(agentMethod -> shouldHandle(path, agentMethod.agent()))
                                .forEach(agentMethod -> {
                                    final Matcher matcher = agentMethod.pattern.matcher(arg);
                                    if (matcher.find()) {
                                        final String name = name(agentMethod.agent().format(), matcher, location, path);
                                        record.computeIfAbsent(name, FunctionHelper.abandon(LinkedList::new)) += agentMethod.method();
                                    }
                                });
                        if (record.values().stream().anyMatch(it -> it.size() > 1))
                            holder.registerProblem(annotationTree, STR."The same path is repeatedly matched by multiple regular expressions.\npath: \{arg}\n  \{record}",
                                    ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
                    });
                }
            } catch (final IOException e) {
                holder.registerProblem(annotationTree, STR."An IO exception occurred when visiting the path corresponding to the ResourceBundle: \{e.getMessage()}",
                        ProblemHighlightType.GENERIC_ERROR, quickFix.createDeleteFix(annotationTree));
            }
        }
    }
    
    protected Path location(final PsiElement context, final ResourceBundle annotation) {
        final String value = annotation.value();
        return !value.isEmpty() && value.charAt(0) == '!' ? Path.of(value.substring(1)) : ProjectKt.getStateStore(context.getProject()).getProjectBasePath() / value;
    }
    
}
