package bear.plugins.misc;

import bear.console.AbstractConsole;
import bear.console.ConsoleCallback;
import bear.console.ConsoleCallbackResult;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.Plugin;
import bear.plugins.sh.CommandLine;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.InstallationTask;
import bear.task.InstallationTaskDef;
import bear.vcs.CommandLineResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class FileWatchDogPlugin extends Plugin {
    private static final Logger logger = LoggerFactory.getLogger(FileWatchDogPlugin.class);

    public final DynamicVariable<Integer>
        timeoutMs = Variables.equalTo(bear.buildTimeoutMs);

    public final DynamicVariable<Boolean>
        reportJavaExceptions = Variables.newVar(true);


    public FileWatchDogPlugin(GlobalContext global) {
        super(global);

    }

    public CommandLineResult watch(final SessionContext $, final WatchDogInput input) {
        int timeoutMs = input.timeoutMs == -1 ? $.var(this.timeoutMs) : input.timeoutMs;

        final boolean reportExceptions = $.var(reportJavaExceptions);


        CommandLine line = $.sys.line().timeoutMs(timeoutMs)
            .sudoOrStty(input.sudo).addSplit("tail -f -n " + input.lines + " ").a(input.path)
            .setCallback(new ConsoleCallback() {
                @Override
                @Nonnull
                public ConsoleCallbackResult progress(AbstractConsole.Terminal console, String buffer, String wholeText) {
                    if (buffer.contains("password")) {
                        console.println($.var($.bear.sshPassword));
                    }
                    if (reportExceptions) {
                        int index = buffer.indexOf("Exception: ");
                        if (index != -1) {
                            logger.warn("exception in {}: {}", buffer.substring(index, buffer.indexOf('\n', index)));
                        }
                    }

                    return input.callback.progress(console, buffer, wholeText);
                }
            });


//        logger.debug("OOOOOOOOOOOOPS - entering watch!!");
        CommandLineResult result = $.sys.sendCommand(line);

//        logger.debug("OOOOOOOOOOOOPS - leaving watch: {}!!", result);

        return result;
    }


    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return InstallationTaskDef.EMPTY;
    }
}
