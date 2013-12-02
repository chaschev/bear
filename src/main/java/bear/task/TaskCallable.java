package bear.task;

import bear.core.SessionContext;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public interface TaskCallable<TASK_DEF extends TaskDef>{
    TaskResult call(SessionContext $, Task<TASK_DEF> task, Object input) throws Exception;
}
