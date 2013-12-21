package bear.maven;

import bear.main.BearFX;
import chaschev.util.Exceptions;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.BaseConfiguration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.slf4j.LoggerFactory;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class LoggingBooter {
    public static void addLog4jAppender(String loggerName, Appender appender, Level level, Filter filter) {
        addLog4jAppender(LogManager.getLogger(loggerName), appender, level, filter);
    }

    public static void addLog4jAppender(org.apache.logging.log4j.Logger log4jLogger, Appender appender, Level level, Filter filter) {
        try {
            org.apache.logging.log4j.core.Logger coreLogger
                = (org.apache.logging.log4j.core.Logger) log4jLogger;

            LoggerContext context = coreLogger.getContext();

            BaseConfiguration configuration
                = (BaseConfiguration) context.getConfiguration();

            configuration.addAppender(appender);
            //this line sort of resets the loggers
//            context.updateLoggers(configuration);

//            if (coreLogger.getParent() == null) {
                for (LoggerConfig loggerConfig : configuration.getLoggers().values()) {
                    loggerConfig.addAppender(appender, level, filter);
//                }
            }
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    public static void loggerDiagnostics() {
        LoggerFactory.getLogger(BearFX.class).debug("MUST NOT BE SEEN started the Bear - -1!");
        LoggerFactory.getLogger("fx").info("started the Bear - 0!");
        LoggerFactory.getLogger("fx").warn("started the Bear - 1!");
        LogManager.getRootLogger().warn("started the Bear - 2!");
        LoggerFactory.getLogger(BearFX.class).warn("started the Bear - 3!");
        LogManager.getLogger(BearFX.class).warn("started the Bear - 4!");
        LoggerFactory.getLogger("fx").debug("started the Bear - 5!");
    }

    public static void changeLogLevel(String loggerName, Level level){
        org.apache.logging.log4j.core.Logger coreLogger
            = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(loggerName);

        coreLogger.setLevel(level);
        System.out.println(coreLogger.getLevel());

        LoggerContext context = coreLogger.getContext();

        BaseConfiguration configuration = (BaseConfiguration) context.getConfiguration();

//        configuration.getLoggerConfig(loggerName).setLevel(level);

        for (LoggerConfig loggerConfig : configuration.getLoggers().values()) {
            if(loggerConfig.getName().startsWith(loggerName)){
                loggerConfig.setLevel(level);
            }
        }

//        context.updateLoggers(configuration);

        LogManager.getLogger("fx").info("should not be seen");
    }
}
