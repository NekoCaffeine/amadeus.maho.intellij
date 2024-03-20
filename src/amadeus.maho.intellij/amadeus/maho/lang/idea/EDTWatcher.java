package amadeus.maho.lang.idea;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.control.Interrupt;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EDTWatcher extends Thread {
    
    Thread target;
    
    Consumer<CompletableFuture<Void>> submit;
    
    @Default
    Consumer<Thread> reporter = reportToLog();
    
    @Default
    long timeout = 1000;
    
    @Default
    int maxReport = Integer.MAX_VALUE;
    
    { setName(STR."EDTWatcher-\{target().getName()}"); }
    
    { setDaemon(true); }
    
    protected void sleep0(final long time = timeout()) = Interrupt.doInterruptible(() -> Thread.sleep(time));
    
    @Override
    public void run() {
        while (target.isAlive()) {
            sleep0(50);
            final CompletableFuture<Void> future = { };
            submit().accept(future);
            if (future.isDone())
                continue;
            sleep0();
            int reportCount = 0;
            while (!future.isDone() && ++reportCount <= maxReport()) {
                reporter().accept(target());
                sleep0();
            }
        }
    }
    
    public static final DateTimeFormatter DEFAULT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS").withZone(ZoneId.systemDefault());
    
    public static Consumer<Thread> reportToLog(final Function<Thread, String> dumper = EDTWatcher::dumpTargetThread, final Path dir = ~Path.of(System.getProperty("user.home"), "EDTReporter"),
            final DateTimeFormatter formatter = DEFAULT_FORMATTER) = thread -> dumper[thread] >> dir / STR."\{LocalDateTime.now().format(formatter)}.stacktrace";
    
    public static String dumpTargetThread(final Thread thread) = Stream.of(thread.getStackTrace())
            .map(StackTraceElement::toString)
            .map("    "::concat)
            .collect(Collectors.joining("\n", STR."\{thread.getName()}\n", ""));
    
    public static @Nullable Thread lookupThread(final String name) = ~Stream.of((Privilege) Thread.getAllThreads()).filter(thread -> thread.getName().equals(name));
    
}
