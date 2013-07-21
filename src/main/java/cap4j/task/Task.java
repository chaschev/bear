package cap4j.task;

import cap4j.Role;
import cap4j.session.Result;
import cap4j.session.SystemEnvironments;

import java.util.ArrayList;
import java.util.List;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public abstract class Task<T extends Task.TaskResult> {
    List<Task> beforeTasks = new ArrayList<Task>();
    List<Task> afterTasks = new ArrayList<Task>();
    List<Task> dependsOnTasks = new ArrayList<Task>();
    List<Role> roles;

    String name;
    String description;

    protected SystemEnvironments system;

    public static class TaskResult{
        Result result;

        public TaskResult(Result result) {
            this.result = result;
        }
    }

    protected abstract T run();

    protected void onRollback(){
        //todo use it
    }


}
