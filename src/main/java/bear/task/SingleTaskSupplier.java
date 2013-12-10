package bear.task;

import bear.core.SessionContext;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public interface SingleTaskSupplier<TASK extends Task> extends TaskDef.TaskSupplier<TASK> {
    TASK createNewSession(SessionContext $, final Task parent, TaskDef<TASK> def);

    public static final SingleTaskSupplier<Task> NOP = Tasks.newSingleTask(TaskCallable.NOP);
}
