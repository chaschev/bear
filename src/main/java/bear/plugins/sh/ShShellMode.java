package bear.plugins.sh;

import bear.core.SessionContext;
import bear.plugins.CommandInterpreter;
import bear.plugins.Plugin;
import bear.plugins.PluginShellMode;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.TaskResult;
import bear.task.TaskRunner;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class ShShellMode extends PluginShellMode implements CommandInterpreter {
    public ShShellMode(Plugin plugin) {
        super(plugin, "sh");
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
