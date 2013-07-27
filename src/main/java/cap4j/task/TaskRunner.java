package cap4j.task;

import cap4j.GlobalContext;
import cap4j.Variables;
import cap4j.session.Result;
import cap4j.session.SessionContext;
import cap4j.session.SystemEnvironment;
import cap4j.task.Task;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public class TaskRunner {
    Set<Task> tasksExecuted = new LinkedHashSet<Task>();

    Variables.Context context;

    public TaskRunner(Variables.Context context) {
        this.context = context;
    }

    public Result run(Task<Task.TaskResult> task){
        Result result = runRec(task);

        if(result != Result.OK){
            for (Task taskExecuted : tasksExecuted) {
                taskExecuted.onRollback();
            }
        }

        return result;
    }

    protected Result runRec(Task<Task.TaskResult> task){
        if(tasksExecuted.contains(task)){
            return Result.OK;
        }

        task.context = context;

        Result runResult = runTasks(task.dependsOnTasks);

        if(runResult != Result.OK){
            return runResult;
        }

        runResult = runTasks(task.beforeTasks);

        if(runResult != Result.OK){
            return runResult;
        }

        runResult = runTasks(Collections.singletonList(task));

        if(runResult!=Result.OK){
            return runResult;
        }

        runResult = runTasks(task.afterTasks);

        if(runResult!=Result.OK){
            return runResult;
        }

        return Result.OK;
    }

    private Result runTasks( List<Task<Task.TaskResult>> tasks) {
        Result runResult = Result.OK;
        for (Task<Task.TaskResult> dependantTask : tasks){
            if(!dependantTask.roles.isEmpty() && !dependantTask.hasRole(context.system.getRoles())){
                continue;
            }

            runResult = runTask(dependantTask);

            if(runResult!=Result.OK){
                break;
            }
        }
        return runResult;
    }

    private Result runTask(Task<Task.TaskResult> task) {
        Result result = null;
        try {
            result = runRec(task);
        } catch (Exception ignore) {

        }

        Result runResult;

        if(result == null || result != Result.OK){
            runResult = Result.ERROR;
        }else{
            runResult = Result.OK;
        }

        return runResult;
    }
}
