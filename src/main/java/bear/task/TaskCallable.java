package bear.task;

import bear.core.SessionContext;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public interface TaskCallable<I, O extends TaskResult<?>>{
    O call(SessionContext $, Task<I, O> task) throws Exception;

    public static final TaskCallable<Object, TaskResult<?>> NOP = new TaskCallable<Object, TaskResult<?>>() {
        @Override
        public TaskResult<?> call(SessionContext $, Task<Object, TaskResult<?>> task) throws Exception {
            return TaskResult.OK;
        }
    };


}
