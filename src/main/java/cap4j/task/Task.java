package cap4j.task;

import cap4j.*;
import cap4j.session.Result;
import cap4j.session.SystemEnvironments;
import com.google.common.collect.Sets;

import java.util.*;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public abstract class Task<T extends TaskResult> {
    List<Task<TaskResult>> beforeTasks = new ArrayList<Task<TaskResult>>();
    List<Task<TaskResult>> afterTasks = new ArrayList<Task<TaskResult>>();
    List<Task<TaskResult>> dependsOnTasks = new ArrayList<Task<TaskResult>>();
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

    protected void defineVars(Console console){

    }

    protected TaskResult run(TaskRunner runner) {
        return new TaskResult(Result.OK);
    }

    public Object var(VariableName varName){
        return context.sessionContext.variables.get(varName.name(), null);
    }


    protected void onRollback(){
        //todo use it
    }

    public String varS(Nameable varName) {
        return context.varS(varName);
    }
}
