package bear.task;

import bear.core.SessionContext;

import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public interface MultitaskSupplier<TASK extends Task> extends TaskDef.TaskSupplier<TASK> {
    List<TASK> createNewSessions(SessionContext $, final Task parent);
    int size();
}
