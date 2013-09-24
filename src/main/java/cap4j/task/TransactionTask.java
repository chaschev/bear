package cap4j.task;

import cap4j.session.GenericUnixRemoteEnvironment;
import cap4j.session.Result;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * User: chaschev
 * Date: 7/27/13
 */
public class TransactionTask extends Task{
    private static final Logger logger = LoggerFactory.getLogger(GenericUnixRemoteEnvironment.class);

    List<Task<TaskResult>> tasks;

    public TransactionTask(List<Task<TaskResult>> tasks) {
        this.tasks = tasks;
    }

    public TransactionTask(Task... tasks) {
        this.tasks = (ArrayList) Lists.newArrayList(tasks);
        name = "transaction of " + tasks.length + " tasks";
    }

    @Override
    protected TaskResult run(TaskRunner runner) {
        Result result = null;
        try{
            result = runner.run(tasks);
        }catch (Exception e){
            logger.warn("", e);
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
