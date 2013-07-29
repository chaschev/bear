package cap4j.task;

import cap4j.GlobalContext;
import cap4j.Nameable;
import cap4j.Variables;
import cap4j.session.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static cap4j.session.Result.ERROR;
import static cap4j.session.Result.OK;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public class TaskRunner {
    private static final Logger logger = LoggerFactory.getLogger(TaskRunner.class);

    LinkedHashSet<Task> tasksExecuted = new LinkedHashSet<Task>();

    Variables.Context context;

    public TaskRunner(Variables.Context context) {
        this.context = context;
    }

    public Result run(Task<TaskResult> task) {
        return runWithDependencies(task);
    }

    public Result run(Task... tasks) {
        return run((List)Arrays.asList(tasks));
    }

    public Result run(Iterable<Task<TaskResult>> tasks) {
        for (Task<TaskResult> task : tasks) {
            final Result result = runWithDependencies(task);

            if (result != OK) {
                return result;
            }
        }

        return OK;
    }

    private void rollbackExecuted(Result result) {
        if (result != OK) {
            //todo fixme reverse!
            try {
                for (Task taskExecuted : tasksExecuted) {
                    taskExecuted.onRollback();
                }
            } finally {
                tasksExecuted.clear();
            }
        }
    }

    protected Result runWithDependencies(Task<TaskResult> task) {
        if (tasksExecuted.contains(task)) {
            return OK;
        }

        task.context = context;

        return Result.and(
            runCollectionOfTasks(task.dependsOnTasks),
            runCollectionOfTasks(task.beforeTasks),
            runMe(task),
            runCollectionOfTasks(task.afterTasks)
        );
    }

    private Result runMe(Task<TaskResult> task) {
        task.defineVars(GlobalContext.console());

        return runCollectionOfTasks(Collections.singletonList(task));
    }

    private Result runCollectionOfTasks(List<Task<TaskResult>> tasks) {
        Result runResult = OK;
        for (Task<TaskResult> task : tasks) {
            if (!task.roles.isEmpty() && !task.hasRole(context.system.getRoles())) {
                continue;
            }

            runResult = _runSingleTask(task);

            if (runResult != OK) {
                break;
            }
        }
        return runResult;
    }

    private Result _runSingleTask(Task<TaskResult> task) {
        Result result = null;
        try {
            result = runWithDependencies(task);
        } catch (Exception ignore) {
            logger.error(ignore.toString());
        }

        Result runResult;

        if (result == null || result != OK) {
            runResult = ERROR;
        } else {
            runResult = OK;
        }

        return runResult;
    }

    public String varS(Nameable varName) {
        return context.sessionContext.variables.get(varName, null);
    }
}
