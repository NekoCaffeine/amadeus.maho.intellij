package amadeus.maho.inteliij.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.jna.Platform;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.transform.AOTTransformer;
import amadeus.maho.util.build.Distributive;
import amadeus.maho.util.build.IDEA;
import amadeus.maho.util.build.Jar;
import amadeus.maho.util.build.Javac;
import amadeus.maho.util.build.Module;
import amadeus.maho.util.build.Workspace;

import static amadeus.maho.util.build.ScriptHelper.*;

@SneakyThrows
public interface Build {
    
    @FieldDefaults(level = AccessLevel.PUBLIC)
    class IntellijConfig {
        
        // install location, usually if you installed IntelliJ using Toolbox, you can find that path inside
        String intellijPath = "missing";
        
        String appendArgs = "";
        
    }
    
    Workspace workspace = Workspace.here();
    
    IntellijConfig config = workspace.config().load(new IntellijConfig()).let(it -> {
        if (!Files.isDirectory(Path.of(it.intellijPath)))
            throw new IllegalArgumentException("IntellijConfig.default.cfg # invalid intellijPath: " + it.intellijPath);
    });
    
    // list of built-in plug-ins to be referenced
    Set<String> plugins = Set.of("java");
    
    // avoid analyzing unnecessary libraries, thereby improving compilation speed
    List<String> shouldInCompile = Stream.of("app", "platform-api", "platform-impl", "util", "util_rt", "spellchecker", "java-api", "java-impl").map(name -> name + Jar.SUFFIX).collect(Collectors.toList());
    
    static Set<Module.Dependency> dependencies() = IDEA.DevKit.attachLocalInstance(Path.of(config.intellijPath), plugins, path -> shouldInCompile.contains(path.getFileName().toString())) += Module.DependencySet.maho();
    
    Module module = { "amadeus.maho.intellij", dependencies() }, run = IDEA.DevKit.run(Path.of(config.intellijPath));
    
    List<Path> useModulePath = Module.SingleDependency.maho().stream().map(Module.SingleDependency::classes).collect(Collectors.toList());
    
    static void sync() {
        IDEA.deleteLibraries(workspace);
        IDEA.generateAll(workspace, "17", true, List.of(Module.build(), module, run));
    }
    
    static Path libPath(final Path pluginsPath) = pluginsPath / module.name() / "lib";
    
    static Path build(final boolean aot = true) {
        workspace.clean(module).flushMetadata();
        Javac.compile(workspace, module, useModulePath::contains, args -> Javac.addReadsAllUnnamed(args, module));
        final Map<String, Jar.Result> pack = Jar.pack(workspace, module);
        final Path modulesDir = workspace.output(Jar.MODULES_DIR, module), targetDir = aot ? ~workspace.output("aot-" + Jar.MODULES_DIR, module) : modulesDir;
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
    
    static void push() {
        build() | root -> libPath(root) >> --~libPath(run.path() / "plugins");
        buildRun();
    }
    
    static void pushHost() = build() | root -> libPath(root) >> --~libPath(Path.of(config.intellijPath + ".plugins"));
    
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
            it += D_PAIR.formatted("jna.boot.library.path", "%s/lib/jna/amd64".formatted(config.intellijPath));
            it += D_PAIR.formatted("pty4j.preferred.native.folder", "%s/lib/pty4j".formatted(config.intellijPath));
            it += D_PAIR.formatted("jna.nosys", true);
            it += D_PAIR.formatted("jna.nounpack", true);
        }
        if (!config.appendArgs.isEmpty())
            it *= List.of(config.appendArgs.split(" "));
    });
    
    Path runDir = workspace.root() / run.path();
    
    int debugPort = 36879;
    
    static Process run() = workspace.run(run, -1, runArgs, true, runDir, _ -> false);
    
    static Process debug() = workspace.run(run, debugPort, runArgs, true, runDir, _ -> false);
    
    static Process runHost() = switch (Platform.getOSType()) {
        case Platform.WINDOWS -> (Path.of(config.intellijPath) / "bin").run(List.of("idea"));
        default               -> throw new IllegalStateException("Unexpected OS Type: " + Platform.getOSType());
    };
    
}
