package amadeus.maho.lang.idea;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.intellij.openapi.diagnostic.JulLogger;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

import static amadeus.maho.util.bytecode.Bytecodes.ALOAD;

/*
    FUCKING SLOW !!!
        [EDT]
        at java.util.regex.Pattern.split(java.base@21.0.1/Pattern.java:1481)
        at com.intellij.openapi.util.text.StringUtil.splitByLines(StringUtil.java:2514)
        at com.intellij.openapi.util.text.StringUtil.splitByLines(StringUtil.java:2501)
        at com.intellij.openapi.diagnostic.IdeaLogRecordFormatter.formatThrowable(IdeaLogRecordFormatter.java:87)
        at com.intellij.openapi.diagnostic.IdeaLogRecordFormatter.format(IdeaLogRecordFormatter.java:55)
        at java.util.logging.StreamHandler.publish0(java.logging@21.0.1/StreamHandler.java:240)
        at java.util.logging.StreamHandler.publish(java.logging@21.0.1/StreamHandler.java:230)
        - locked <0x0000040032008838> (a com.intellij.openapi.diagnostic.RollingFileHandler)
        at com.intellij.openapi.diagnostic.RollingFileHandler.publish(RollingFileHandler.kt:57)
        at java.util.logging.Logger.log(java.logging@21.0.1/Logger.java:983)
        at java.util.logging.Logger.doLog(java.logging@21.0.1/Logger.java:1010)
        at java.util.logging.Logger.log(java.logging@21.0.1/Logger.java:1121)
        at com.intellij.openapi.diagnostic.JulLogger.info(JulLogger.java:65)
        at com.intellij.psi.impl.DebugUtil.handleUnspecifiedTrace(DebugUtil.java:598)
 */
@TransformProvider
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AsyncLogger extends Logger {
    
    Logger logger;
    
    LinkedBlockingQueue<LogRecord> records = { };
    
    Thread loggingThread = new Thread() {
        
        { setName("JUL-AsyncLogging"); }
        
        { setDaemon(true); }
        
        @Override
        @SneakyThrows
        public void run() = Stream.generate(records()::take).forEach(logger()::log);
        
    };
    
    protected AsyncLogger(final Logger logger) {
        super(logger.getName(), logger.getResourceBundleName());
        this.logger = logger;
    }
    
    @Override
    public void log(final LogRecord record) = records += record;
    
    @Hook(at = @At(var = @At.VarInsn(opcode = ALOAD, var = 1)), before = false, capture = true)
    private static AsyncLogger _init_(final Logger capture, final JulLogger $this, final Logger delegate) = { capture };
    
    
}
