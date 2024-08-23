package amadeus.maho.intellij.build;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.Resolver;
import java.net.URI;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.module.ModulePath;
import jdk.tools.jlink.builder.DefaultImageBuilder;
import jdk.tools.jlink.internal.Jlink;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.ModuleExportNode;
import org.objectweb.asm.tree.ModuleNode;
import org.objectweb.asm.tree.ModuleOpenNode;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.build.Workspace;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.Bytecodes;
import amadeus.maho.util.bytecode.ClassWriter;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.resource.ResourcePath;

import static amadeus.maho.util.bytecode.ASMHelper.*;

@SneakyThrows
@TransformProvider
public interface RuntimeMerger {
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    class JBRConfig {
        
        String root = "<missing>";
        
    }
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class MergeJavaBasePlugin implements Plugin {
        
        Path jbrSdkDir;
        
        private static final Method
                GET    = { "get", TYPE_OBJECT, new Type[]{ TYPE_OBJECT } },
                PUT    = { "put", TYPE_OBJECT, new Type[]{ TYPE_OBJECT, TYPE_OBJECT } },
                CONCAT = { "concat", TYPE_STRING, new Type[]{ TYPE_STRING } };
        
        private static final Map<String, String> copyMissingMethod = Stream.of("jdk.internal.access.JavaLangInvokeAccess", "java.lang.invoke.MethodHandleImpl$1")
                .collect(Collectors.toMap(it -> STR."/java.base/\{it.replace('.', '/')}.class", Function.identity()));
        
        @Override
        public ResourcePool transform(final ResourcePool in, final ResourcePoolBuilder out) {
            try (final ResourcePath.ResourceTree resourceTree = ResourcePath.ResourceTree.of(jbrSdkDir / "jmods" / JAVA_BASE_JMOD)) {
                in.transformAndCopy(resource -> {
                    if (resource.type() == ResourcePoolEntry.Type.CLASS_OR_RESOURCE)
                        if (resource.path().equals("/java.base/java/lang/VersionProps.class")) {
                            final ClassNode node = ASMHelper.newClassNode(resource.contentBytes());
                            ASMHelper.lookupMethodNode(node, "init", "(Ljava/util/Map;)V").ifPresent(method -> {
                                final InsnList shadow = { };
                                final MethodGenerator generator = MethodGenerator.fromShadowMethodNode(method, shadow);
                                replaceVendor(generator, "java.vendor");
                                replaceVendor(generator, "java.specification.vendor");
                                replaceVendor(generator, "java.vm.vendor");
                                replaceVendor(generator, "java.vm.specification.vendor");
                                final InsnNode returnNode = (~method.instructions.fromIterable().cast(InsnNode.class).filter(it -> it.getOpcode() == Bytecodes.RETURN))!;
                                method.instructions.insertBefore(returnNode, shadow);
                                System.out.println("Replace vendor in VersionProps");
                            });
                            return resource.copyWithContent(ClassWriter.toBytecode(node::accept));
                        } else if (resource.path().equals("/java.base/module-info.class")) {
                            final ClassNode node = ASMHelper.newClassNode(resource.contentBytes());
                            final ModuleNode moduleNode = node.module;
                            jbrSubPackages.forEach(it -> moduleNode.packages += STR."\{JETBRAINS_API_PACKAGE_INTERNAL_NAME}\{it}");
                            moduleNode.opens ?? (moduleNode.opens = new ArrayList<>()) += new ModuleOpenNode(STR."\{JETBRAINS_API_PACKAGE_INTERNAL_NAME}bootstrap", 0, null);
                            moduleNode.exports ?? (moduleNode.exports = new ArrayList<>()) += new ModuleExportNode(STR."\{JETBRAINS_API_PACKAGE_INTERNAL_NAME}exported", 0, null);
                            System.out.println("Add JBR API to module-info");
                            return resource.copyWithContent(ClassWriter.toBytecode(node::accept));
                        } else {
                            final @Nullable String className = copyMissingMethod[resource.path()];
                            if (className != null) {
                                final @Nullable ResourcePath.ClassInfo classInfo = resourceTree.findClassInfo(className);
                                if (classInfo != null) {
                                    final ClassNode baseNode = ASMHelper.newClassNode(resource.contentBytes()), jbrNode = ASMHelper.newClassNode(classInfo.readAll());
                                    jbrNode.methods.forEach(methodNode -> {
                                        if (ASMHelper.lookupMethodNode(baseNode, methodNode.name, methodNode.desc).isEmpty()) {
                                            baseNode.methods.add(methodNode);
                                            System.out.println(STR."Copy missing method: \{className}: \{methodNode.name}\{methodNode.desc}");
                                        }
                                    });
                                    return resource.copyWithContent(ClassWriter.toBytecode(baseNode::accept));
                                }
                            }
                        }
                    return resource;
                }, out);
                resourceTree.classes()
                        .filter(info -> info.className().startsWith(JETBRAINS_API_PACKAGE_NAME))
                        .forEach(info -> out.add(ResourcePoolEntry.create(STR."/java.base/\{info.root() % info.path()}", ResourcePoolEntry.Type.CLASS_OR_RESOURCE, info.readAll())));
                final @Nullable ResourcePath.ResourceInfo jbrapi = resourceTree.findResource("classes/META-INF/jbrapi.registry");
                if (jbrapi != null) {
                    out.add(ResourcePoolEntry.create("/java.base/META-INF/jbrapi.registry", ResourcePoolEntry.Type.CLASS_OR_RESOURCE, jbrapi.readAll()));
                    System.out.println("Copy jbrapi.registry");
                }
            }
            final Path options = Path.of(URI.create("jrt:/java.base/jdk/internal/vm/options"));
            if (Files.isRegularFile(options))
                out.add(ResourcePoolEntry.create("/java.base/jdk/internal/vm/options", Files.readAllBytes(options)));
            return out.build();
        }
        
        private void replaceVendor(final MethodGenerator generator, final String key) {
            generator.loadArg(0);
            generator.push(key);
            generator.loadArg(0);
            generator.push(key);
            generator.invokeInterface(TYPE_MAP, GET);
            generator.push(" & JetBrains s.r.o");
            generator.invokeVirtual(TYPE_STRING, CONCAT);
            generator.invokeInterface(TYPE_MAP, PUT);
        }
        
    }
    
    @Hook(forceReturn = true)
    private static void checkHashes(final Resolver $this) { }
    
    String
            JAVA_BASE_JMOD                      = "java.base.jmod",
            JAVA_DESKTOP_JMOD                   = "java.desktop.jmod",
            JETBRAINS_API_PACKAGE_NAME          = "com.jetbrains.",
            JETBRAINS_API_PACKAGE_INTERNAL_NAME = "com/jetbrains/";
    
    List<String> jbrSubPackages = List.of("internal", "exported", "bootstrap");
    
    Workspace workspace = Workspace.here();
    
    JBRConfig jbrConfig = workspace.config().load(new JBRConfig());
    
    Predicate<Path> isJavaDesktopJMod = it -> it.getFileName().toString().equals(JAVA_DESKTOP_JMOD);
    
    static Path merge(final Path jbrSdkDir = Path.of(jbrConfig.root), final Path baseJMods = Path.of(System.getProperty("java.home"), "jmods")) {
        final Path output = --workspace.output("merged-image");
        final ArrayList<Path> paths = { };
        final Set<String> baseModules = Files.list(baseJMods).map(it -> it.getFileName().toString()).collect(Collectors.toUnmodifiableSet());
        paths *= Files.list(baseJMods).filterNot(isJavaDesktopJMod).toList();
        paths += jbrSdkDir / "jmods" / JAVA_DESKTOP_JMOD;
        paths *= Files.list(jbrSdkDir / "jmods").filterNot(jmod -> baseModules[jmod.getFileName().toString()]).toList();
        final ModuleFinder finder = ModulePath.of(Runtime.version(), true, paths.toArray(new Path[0]));
        final Set<String> modules = finder.findAll().stream().map(ModuleReference::descriptor).map(ModuleDescriptor::name).collect(Collectors.toUnmodifiableSet());
        final Jlink.JlinkConfiguration jlinkConfiguration = { output, modules, ByteOrder.nativeOrder(), finder };
        final MergeJavaBasePlugin plugin = { jbrSdkDir };
        final List<Plugin> plugins = List.of(plugin);
        final Map<String, String> launchers = Map.of();
        final DefaultImageBuilder builder = { output, launchers };
        final Jlink.PluginsConfiguration pluginsConfiguration = { plugins, builder, null };
        amadeus.maho.util.build.Jlink.build(jlinkConfiguration, pluginsConfiguration);
        return output;
    }
    
}
