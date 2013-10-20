package bear.plugins;

import bear.core.SessionContext;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.TaskResult;
import bear.task.TaskRunner;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class PluginShellMode implements CommandInterpreter {
    protected String commandName;
    protected String description;

    protected PluginShellMode(String commandName) {
        this.commandName = commandName;
    }

    public static class SshShellMode extends PluginShellMode implements CommandInterpreter{
        public SshShellMode() {
            super("ssh");
        }

        public Task interpret(final String command, SessionContext $, Task parent, TaskDef taskDef){
            return new Task<TaskDef>(parent, taskDef, $) {
                @Override
                protected TaskResult exec(TaskRunner runner) {
                    return $.sys.script()
                        .timeoutSec(60)
                        .line().addRaw(command).build()
                        .run();
                }
            };
        }
    }

    public String getCommandName() {
        return commandName;
    }

    @Override
    public String toString() {
        return commandName;
    }
}
