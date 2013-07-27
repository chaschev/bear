package cap4j.task;

import cap4j.Console;
import cap4j.Role;
import cap4j.VariableName;
import cap4j.Variables;
import cap4j.session.Result;
import cap4j.session.SystemEnvironments;
import com.google.common.collect.Sets;

import java.util.*;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public abstract class Task<T extends Task.TaskResult> {
    List<Task<Task.TaskResult>> beforeTasks = new ArrayList<Task<Task.TaskResult>>();
    List<Task<Task.TaskResult>> afterTasks = new ArrayList<Task<Task.TaskResult>>();
    List<Task<Task.TaskResult>> dependsOnTasks = new ArrayList<Task<Task.TaskResult>>();
    Set<Role> roles;

    String name;
    String description;

    protected transient Variables.Context context;

    protected SystemEnvironments system;

    public boolean hasRole(Set<Role> roles) {
        return !Sets.intersection(this.roles, roles).isEmpty();
    }

    public Task<T> setDependsOnTasks(Task... tasks) {
        dependsOnTasks.clear();
        Collections.addAll(dependsOnTasks, tasks);

        return this;
    }

    public static class TaskResult{
        Result result;

        public TaskResult(Result result) {
            this.result = result;
        }
    }

    protected void defineVars(Console console){

    }

    protected TaskResult run() {
        return new TaskResult(Result.OK);
    }

    public Object var(VariableName varName){
        return context.sessionContext.variables.get(context.sessionContext, varName.name(), null);
    }


    protected void onRollback(){
        //todo use it
    }


}
