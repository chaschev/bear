package cap4j.task;

import cap4j.*;
import cap4j.session.DynamicVariable;
import cap4j.session.Result;
import cap4j.session.SystemEnvironment;
import cap4j.session.SystemEnvironments;
import com.google.common.collect.Sets;

import java.util.*;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public abstract class Task<T extends TaskResult> {
    String name;
    String description;

    Set<Role> roles = new HashSet<Role>();

    List<Task<TaskResult>> beforeTasks = new ArrayList<Task<TaskResult>>();
    List<Task<TaskResult>> afterTasks = new ArrayList<Task<TaskResult>>();
    List<Task<TaskResult>> dependsOnTasks = new ArrayList<Task<TaskResult>>();

    public Task() {
    }

    public Task(String name) {
        this.name = name;
    }

    protected transient VarContext context;

    protected SystemEnvironment system;

    public boolean hasRole(Set<Role> roles) {
        return !Sets.intersection(this.roles, roles).isEmpty();
    }

    public Task setDependsOnTasks(Task... tasks) {
        dependsOnTasks.clear();
        Collections.addAll((List)dependsOnTasks, tasks);

        return this;
    }

    protected void defineVars(Console console){

    }

    protected TaskResult run(TaskRunner runner) {
        return new TaskResult(Result.OK);
    }


    public <T> T var(DynamicVariable<T> varName){
        return context.var(varName);
    }

    public <T> T var(Nameable<T> varName){
        return context.var(varName);
    }


    protected void onRollback(){
        //todo use it
    }

    public Task<T> addBeforeTask(Task task) {
        beforeTasks.add(task);
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Task{");
        sb.append("name='").append(name).append('\'');
        sb.append(", description='").append(description).append('\'');
        sb.append(", roles=").append(roles);
        sb.append('}');
        return sb.toString();
    }

    public void setContext(VarContext context) {
        this.context = context;
        this.system = context.system;
    }
}
