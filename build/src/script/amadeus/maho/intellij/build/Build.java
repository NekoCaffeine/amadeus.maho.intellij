package amadeus.maho.intellij.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.AOTTransformer;
import amadeus.maho.util.build.Distributive;
import amadeus.maho.util.build.HotSwap;
import amadeus.maho.util.build.IDEA;
import amadeus.maho.util.build.Jar;
import amadeus.maho.util.build.Javac;
import amadeus.maho.util.build.Module;
import amadeus.maho.util.build.Workspace;
import amadeus.maho.util.misc.Environment;

import static amadeus.maho.util.build.ScriptHelper.*;

@SneakyThrows
public interface Build {
    
    {
        final @Nullable String IDEA_JDK = System.getenv("IDEA_JDK");
        if (IDEA_JDK != null)
            Environment.local()[MAHO_JAVA_EXECUTION] = (Path.of(IDEA_JDK) / "bin" / "java").toAbsolutePath().toString();
    }
    
    @FieldDefaults(level = AccessLevel.PUBLIC)
    class IntellijConfig {
        
        // install location, usually if you installed IntelliJ using Toolbox, you can find that path inside
        String intellijPath = "missing";
        
        String intellijVersion = "?";
        
        String appendArgs = "";
        
        public String intellijVersion() = IDEA.DevKit.inferInstanceMetadata(Path.of(intellijPath)).v2;
        
    }
    
    Workspace workspace = Workspace.here();
    
    IntellijConfig config = workspace.config().load(new IntellijConfig()).let(it -> {
        if (!Files.isDirectory(Path.of(it.intellijPath)))
            throw new IllegalArgumentException(STR."IntellijConfig.default.cfg # invalid intellijPath: \{it.intellijPath}");
    });
    
    // list of built-in plug-ins to be referenced
    Set<String> plugins = Set.of("java");
    
    // avoid analyzing unnecessary libraries, thereby improving compilation speed
    List<String> shouldInCompile = Stream.of("platform-loader", "app", "app-client", "platform-loader", "lib", "lib-client", "util", "util-8", "util_rt", "spellchecker", "java-frontback", "java-impl")
            .map(name -> name + Jar.SUFFIX).collect(Collectors.toList());
    
    Module.DependencySet
            ddlc = { "DDLC", Files.list(workspace.root() / "ddlc").filter(path -> path.getFileName().toString().endsWith(Jar.SUFFIX)).map(Path::toAbsolutePath).map(Module.SingleDependency::new).collect(Collectors.toSet()) },
            copilot = { "Copilot", Files.list(workspace.root() / "copilot").filter(path -> path.getFileName().toString().endsWith(Jar.SUFFIX)).map(Path::toAbsolutePath).map(Module.SingleDependency::new).collect(Collectors.toSet()) };
    
    static Set<Module.Dependency> dependencies() = IDEA.DevKit.attachLocalInstance(Path.of(config.intellijPath), plugins, path -> shouldInCompile.contains(path.getFileName().toString())) *= List.of(Module.DependencySet.maho(), ddlc, copilot);
    
    Module module = { "amadeus.maho.intellij", dependencies() }, run = IDEA.DevKit.run(Path.of(config.intellijPath));
    
    List<Path> useModulePath = Module.SingleDependency.maho().stream().map(Module.SingleDependency::classes).collect(Collectors.toList());
    
    static void sync() {
        IDEA.deleteLibraries(workspace);
        IDEA.generateAll(workspace, "21", true, List.of(Module.build(), module, run));
    }
    
    static Path libPath(final Path pluginsPath) = pluginsPath / module.name() / "lib";
    
    static Path build(final boolean aot = false) {
        workspace.clean(module).flushMetadata();
        Javac.compile(workspace, module, useModulePath::contains, args -> Javac.addReadsAllUnnamed(args, module));
        final Map<String, Jar.Result> pack = Jar.pack(workspace, module, Jar.manifest(), (a, b) -> {
            if (a.getFileName().toString().equals("plugin.xml"))
                Files.readString(a).replace("${version}", STR."\{workspace.config().load(new Module.Metadata(), module.name()).version}-\{config.intellijVersion()}") >> b;
            else
                a >> b;
        });
        final Path modulesDir = workspace.output(Jar.MODULES_DIR, module), targetDir = aot ? ~workspace.output(STR."aot-\{Jar.MODULES_DIR}", module) : modulesDir;
        if (aot)
            AOTTransformer.transform(modulesDir, targetDir);
        return Distributive.zip(workspace, module, root -> {
            final Path lib = ~libPath(root);
            Module.SingleDependency.maho().stream()
                    .filter(dependency -> !dependency.classes().fileName().startsWith("jna-")) // see idea.bat
                    .forEach(dependency -> dependency.classes() >> lib);
            pack.values().forEach(result -> (aot ? targetDir / (modulesDir % result.modules()).toString() : result.modules()) >> lib);
        });
    }
    
    static void hotswap() = Javac.compile(workspace, module, useModulePath::contains, args -> Javac.addReadsAllUnnamed(args, module));
    
    static void push(final Path build = build()) {
        build | root -> libPath(root) >> --~libPath(run.path() / "plugins");
        buildRun();
    }
    
    static void pushHost() = build() | root -> libPath(root) >> --~libPath(Path.of(STR."\{config.intellijPath}.plugins"));
    
    static void buildRun() {
        workspace.clean(run);
        Javac.compile(workspace, run, useModulePath::contains, args -> Javac.addReadsAllUnnamed(args, run));
        Jar.pack(workspace, run, Jar.manifest("amadeus.maho.intellij.run.Main"));
    }
    
    List<String> runArgs = new ArrayList<String>().let(it -> {
        Javac.addReadsAllUnnamed(it, run);
        addModules(it, ALL_SYSTEM);
        addAgent(it, workspace.root() / run.path() / "agent" / "intellij.agent.jar");
        { // without jbr
            it += "--add-opens";
            it += "java.base/java.lang=ALL-UNNAMED";
        }
        it += D_PAIR.formatted("idea.vendor.name", "NekoCaffeine"); // in dev flag
        { // see idea.bat
            it += D_PAIR.formatted("java.system.class.loader", "com.intellij.util.lang.PathClassLoader");
            it += D_PAIR.formatted("jna.boot.library.path", W_QUOTES.formatted(STR."\{config.intellijPath}/lib/jna/amd64"));
            it += D_PAIR.formatted("pty4j.preferred.native.folder", W_QUOTES.formatted(STR."\{config.intellijPath}/lib/pty4j"));
            it += D_PAIR.formatted("jna.nosys", true);
            it += D_PAIR.formatted("jna.nounpack", true);
        }
        if (!config.appendArgs.isEmpty())
            it *= List.of(config.appendArgs.split(" "));
    });
    
    List<String> fastArgs = List.of("-XX:CICompilerCount=24", "-XX:CompileThresholdScaling=0.05");
    
    Path runDir = workspace.root() / run.path();
    
    int debugPort = 36879;
    
    static void fast() = runArgs *= fastArgs;
    
    static void slow() = runArgs /= fastArgs;
    
    static Process run() = workspace.run(run, -1, runArgs, true, runDir, _ -> false);
    
    static Process debug() = workspace.run(run, debugPort, HotSwap.addWatchProperty(runArgs, workspace, module), true, runDir, _ -> false);
    
    static Process runHost() = workspace.run(Path.of(config.intellijPath) / "bin", List.of("idea"));
    
}
