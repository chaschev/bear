package bear.plugins.misc;

import bear.cli.CommandLine;
import bear.console.AbstractConsole;
import bear.console.ConsoleCallback;
import bear.console.ConsoleCallbackResult;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.Plugin;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.InstallationTask;
import bear.task.InstallationTaskDef;

import javax.annotation.Nonnull;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class FileWatchDogPlugin extends Plugin {
    public final DynamicVariable<Integer>
        timeoutMs = Variables.equalTo(bear.buildTimeoutMs);


    public FileWatchDogPlugin(GlobalContext global) {
        super(global);

    }

    public void watch(final SessionContext $, final WatchDogInput input){
        int timeoutMs = input.timeoutMs == -1 ? $.var(this.timeoutMs) : input.timeoutMs;

        CommandLine line = $.sys.line().timeoutMs(timeoutMs)
            .sudoOrStty(input.sudo).addSplit("tail -f -n " + input.lines + " ").a(input.path);

        $.sys.sendCommand(line, new ConsoleCallback() {
            @Override
            @Nonnull
            public ConsoleCallbackResult progress(AbstractConsole.Terminal console, String buffer, String wholeText) {
                if(buffer.contains("password")){
                    console.println($.var($.bear.sshPassword));
                }

                return input.callback.progress(console, buffer, wholeText);
            }
        });
    }



    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return InstallationTaskDef.EMPTY;
    }
}
