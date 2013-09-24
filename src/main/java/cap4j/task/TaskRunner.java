package cap4j.task;

import cap4j.core.SessionContext;
import cap4j.core.GlobalContext;
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

    public SessionContext $;
    public GlobalContext global;

    public TaskRunner(SessionContext $, GlobalContext global) {
        this.$ = $;
        this.global = global;
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
        logger.info("starting task '{}'", task.name);

        if (tasksExecuted.contains(task)) {
            return OK;
        }

        task.$ = $;

        return Result.and(
            runCollectionOfTasks(task.dependsOnTasks, task.name + ": depending tasks", false),
            runCollectionOfTasks(task.beforeTasks, task.name + ": before tasks", false),
            runMe(task),
            runCollectionOfTasks(task.afterTasks, task.name + ": after tasks", false)
        );
    }

    private Result runMe(Task<TaskResult> task) {
        task.defineVars(global.console());

        return runCollectionOfTasks(Collections.singletonList(task), task.name + ": running myself", true);
    }

    private Result runCollectionOfTasks(List<Task<TaskResult>> tasks, String desc, boolean thisIsMe) {
        if(!tasks.isEmpty() && !desc.isEmpty()){
            logger.info(desc);
        }

        Result runResult = OK;
        for (Task<TaskResult> task : tasks) {
            if (!task.roles.isEmpty() && !task.hasRole($.system.getRoles())) {
                continue;
            }

            runResult = _runSingleTask(task, thisIsMe);

            if (runResult != OK) {
                break;
            }
        }
        return runResult;
    }

    private Result _runSingleTask(Task<TaskResult> task, boolean thisIsMe) {
        Result result = null;
        try {
            if(!thisIsMe){
                result = runWithDependencies(task);
            }else{
                task.set$($);
                result = task.run(this).result;
            }
        } catch (Exception ignore) {
            logger.error("", ignore);
        }

        Result runResult;

        if (result == null || result != OK) {
            runResult = ERROR;
        } else {
            runResult = OK;
        }

        return runResult;
    }

}
