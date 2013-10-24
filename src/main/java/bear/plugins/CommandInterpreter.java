package bear.plugins;

import bear.core.SessionContext;
import bear.core.Stage;
import bear.task.Task;
import bear.task.TaskDef;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public interface CommandInterpreter {
    Stage getStage();
    Task<?> interpret(String command, SessionContext $, Task parent, TaskDef taskDef);
}
