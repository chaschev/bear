package cap4j.task;

import cap4j.core.SessionContext;
import cap4j.plugins.HavingContext;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class Task extends HavingContext<Task> {
    private TaskDef parent;

    public Task(TaskDef parent, SessionContext $) {
        super($);

        this.parent = parent;
    }

    protected abstract TaskResult run(TaskRunner runner) ;

    private static final Task NOP_TASK = new Task(null, null) {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return TaskResult.OK;
        }
    };

    public static Task nop() {
        return NOP_TASK;
    }


    protected void onRollback() {
        //todo use it
    }
}
