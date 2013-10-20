package bear.plugins;

import bear.core.SessionContext;
import bear.task.Task;
import bear.task.TaskDef;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public interface CommandInterpreter {
    Task<?> interpret(String command, SessionContext $, Task parent, TaskDef taskDef);
}
