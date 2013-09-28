package cap4j.task;

import cap4j.core.SessionContext;
import cap4j.plugins.HavingContext;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class Task extends HavingContext<Task> {
    private TaskDef parent;
    private Dependencies dependencies = new Dependencies();



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

    public Dependencies getDependencies(){
        return dependencies;
    }

    protected void onRollback() {
        //todo use it
    }

    @Override
    public String toString() {
        return parent == null ? getClass().getSimpleName() : parent.toString();
    }

    public Task addDependency(Dependency... dependencies) {
        this.dependencies.addDependencies(dependencies);
        return this;
    }
}
