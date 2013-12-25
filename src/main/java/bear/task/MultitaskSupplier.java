package bear.task;

import bear.core.SessionContext;

import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public interface MultitaskSupplier<TASK extends Task> extends TaskDef.TaskSupplier<TASK> {
    @Deprecated
    List<TASK> createNewSessions(SessionContext $, final Task parent);
    List<TaskDef<Task>> getTaskDefs();
    int size();
}
