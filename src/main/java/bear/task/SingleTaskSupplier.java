package bear.task;

import bear.core.SessionContext;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public interface SingleTaskSupplier<I, O extends TaskResult> extends TaskDef.TaskSupplier {
    Task<I, O> createNewSession(SessionContext $, final Task<Object, TaskResult> parent, TaskDef<I, O> def);

    public static final SingleTaskSupplier<Object, TaskResult> NOP = Tasks.newSingleSupplier(TaskCallable.NOP);

}
