package bear.task;

import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public interface MultitaskSupplier extends TaskDef.TaskSupplier {
    List<TaskDef<Object, TaskResult>> getTaskDefs();
    int size();
}
