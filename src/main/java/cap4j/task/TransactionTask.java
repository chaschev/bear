package cap4j.task;

import cap4j.session.Result;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

/**
 * User: chaschev
 * Date: 7/27/13
 */
public class TransactionTask extends Task{
    List<Task<TaskResult>> tasks;

    public TransactionTask(List<Task<TaskResult>> tasks) {
        this.tasks = tasks;
    }

    public TransactionTask(Task... tasks) {
        final ArrayList tasks1 = Lists.newArrayList(tasks);
        this.tasks = tasks1;
        name = "transaction of " + tasks.length + " tasks";
    }

    @Override
    protected TaskResult run(TaskRunner runner) {
        Result result = null;
        try{
            result = runner.run(tasks);
        }catch (Exception e){
            result = Result.ERROR;
        }

        if(result != Result.OK){
            //let's keep it simple
            for (Task<TaskResult> task : tasks) {
                task.onRollback();
            }
        }

        return new TaskResult(result);
    }
}
