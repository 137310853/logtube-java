package io.github.logtube;

import io.github.logtube.logger.RootLogger;
import io.github.logtube.outputs.EventConsoleOutput;
import io.github.logtube.outputs.EventJSONFileOutput;
import io.github.logtube.outputs.EventPlainFileOutput;
import io.github.logtube.outputs.EventRemoteOutput;
import org.jetbrains.annotations.NotNull;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class LogtubeLoggerFactory implements ILoggerFactory {

    private static final LogtubeLoggerFactory SINGLETON = new LogtubeLoggerFactory();

    public static LogtubeLoggerFactory getSingleton() {
        return SINGLETON;
    }

    private final ConcurrentMap<String, IEventLogger> derivedLoggers = new ConcurrentHashMap<>();

    private final IRootEventLogger rootLogger;

    private LogtubeLoggerFactory() {
        LogtubeOptions options = LogtubeOptions.fromClasspath();

        RootLogger rootLogger = new RootLogger();

        rootLogger.setHostname(LogtubeOptions.getHostname());
        rootLogger.setProject(options.getProject());
        rootLogger.setEnv(options.getEnv());
        rootLogger.setTopics(options.getTopics());

        if (options.getConsoleEnabled()) {
            EventConsoleOutput output = new EventConsoleOutput();
            output.setTopics(options.getConsoleTopics());
            rootLogger.addOutput(output);
        }

        if (options.getFilePlainEnabled()) {
            EventPlainFileOutput output = new EventPlainFileOutput(
                    options.getFilePlainDir(),
                    options.getFilePlainSignal()
            );
            output.setTopics(options.getFilePlainTopics());
            rootLogger.addOutput(output);
        }

        if (options.getFileJSONEnabled()) {
            EventJSONFileOutput output = new EventJSONFileOutput(
                    options.getFileJSONDir(),
                    options.getFileJSONSignal()
            );
            output.setTopics(options.getFileJSONTopics());
            rootLogger.addOutput(output);
        }

        if (options.getRemoteEnabled()) {
            EventRemoteOutput output = new EventRemoteOutput(
                    options.getRemoteHosts()
            );
            output.setTopics(options.getRemoteTopics());
            rootLogger.addOutput(output);
        }

        this.rootLogger = rootLogger;
    }

    @NotNull
    public IRootEventLogger getRootLogger() {
        return this.rootLogger;
    }

    @NotNull
    public IEventLogger getDerivedLogger(@NotNull String name) {
        IEventLogger logger = this.derivedLoggers.get(name);
        if (logger != null) {
            return logger;
        } else {
            IEventLogger newInstance = this.rootLogger.derive(name, null);
            IEventLogger oldInstance = this.derivedLoggers.putIfAbsent(name, newInstance);
            return oldInstance == null ? newInstance : oldInstance;
        }
    }

    @Override
    public Logger getLogger(String name) {
        return getDerivedLogger(name);
    }

}
