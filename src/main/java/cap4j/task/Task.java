/*
 * Copyright (C) 2013 Andrey Chaschev.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cap4j.task;

import cap4j.core.Console;
import cap4j.core.Role;
import cap4j.core.SessionContext;
import cap4j.session.DynamicVariable;
import cap4j.session.Result;
import cap4j.session.SystemEnvironment;
import com.google.common.collect.Sets;

import java.util.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class Task<T extends TaskResult> {
    String name;
    String description;

    private boolean setupTask;

    Set<Role> roles = new HashSet<Role>();

    protected transient SessionContext $;

    List<Task<TaskResult>> beforeTasks = new ArrayList<Task<TaskResult>>();
    List<Task<TaskResult>> afterTasks = new ArrayList<Task<TaskResult>>();
    List<Task<TaskResult>> dependsOnTasks = new ArrayList<Task<TaskResult>>();

    public Task() {
    }

    protected Task(String name) {
        this.name = name;
    }

    protected SystemEnvironment system;

    public boolean hasRole(Set<Role> roles) {
        return !Sets.intersection(this.roles, roles).isEmpty();
    }

    public Task setDependsOnTasks(Task... tasks) {
        dependsOnTasks.clear();
        Collections.addAll((List) dependsOnTasks, tasks);

        return this;
    }

    protected void defineVars(Console console) {

    }

    protected TaskResult run(TaskRunner runner) {
        return new TaskResult(Result.OK);
    }

    public <T> T var(DynamicVariable<T> varName) {
        return $.var(varName);
    }

    protected void onRollback() {
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

    public Task<T> setCtx(SessionContext $) {
        this.$ = $;
        this.system = $.system;
        return this;
    }

    public Task desc(String description) {
        this.description = description;
        return this;
    }

    public final boolean verifyExecution() {
        return verifyExecution(true);
    }

    public final boolean verifyExecution(boolean throwException) {
        return verify(throwException);
    }

    protected boolean verify(boolean throwException) {
        return true;
    }

    public boolean isSetupTask() {
        return setupTask;
    }

    public Task<T> setSetupTask(boolean setupTask) {
        this.setupTask = setupTask;
        return this;
    }

    public <T> T $(DynamicVariable<T> varName) {
        return $.var(varName);
    }

    private static final Task NOP_TASK = new Task("nop") {

    };

    public static Task nop() {
        return NOP_TASK;
    }
}
