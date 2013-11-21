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

package bear.task;

import bear.core.SessionContext;
import bear.core.Console;
import bear.core.Role;
import com.google.common.collect.Sets;

import java.util.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class TaskDef<TASK extends Task> {

    String name;
    public String description;

    private boolean setupTask;

    Set<Role> roles = new HashSet<Role>();

    List<TaskDef> beforeTasks = new ArrayList<TaskDef>();
    List<TaskDef> afterTasks = new ArrayList<TaskDef>();
    List<TaskDef> dependsOnTasks = new ArrayList<TaskDef>();

    public TaskDef() {
        this.name = classNameToTaskName(getClass().getSimpleName()).toString();
    }

    private StringBuilder classNameToTaskName(String simpleName) {
        int wordStart = 0;
        StringBuilder sb = new StringBuilder(simpleName.length() + 10);
        for (int i = 1; i < simpleName.length(); i++) {
            if(Character.isUpperCase(simpleName.charAt(i))){
                sb.append(simpleName.substring(wordStart, i));
                sb.append(' ');
                wordStart = i;
            }
        }

        sb.append(simpleName.substring(wordStart));
        return sb;
    }

    protected TaskDef(String name) {
        this.name = name;
    }

    public abstract TASK newSession(SessionContext $, final Task parent);

    public boolean hasRole(Set<Role> roles) {
        return !Sets.intersection(this.roles, roles).isEmpty();
    }

    public TaskDef depends(Task<TaskDef>... tasks) {
        Collections.addAll((List) dependsOnTasks, tasks);

        return this;
    }

    protected void defineVars(Console console) {

    }

    public TaskDef addBeforeTask(TaskDef task) {
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

    public TaskDef desc(String description) {
        this.description = description;
        return this;
    }

    public final DependencyResult checkDependencies() {
        return checkDependencies(true);
    }

    public final DependencyResult checkDependencies(boolean throwException) {
        return checkDeps(throwException);
    }

    protected DependencyResult checkDeps(boolean throwException) {
        return DependencyResult.OK;
    }

    public boolean isSetupTask() {
        return setupTask;
    }

    public TaskDef setSetupTask(boolean setupTask) {
        this.setupTask = setupTask;
        return this;
    }

    public static final TaskDef EMPTY = new TaskDef() {
        {
            name = "EMPTY";
        }
        @Override
        public Task<TaskDef> newSession(SessionContext $, final Task parent) {
            return Task.nop();
        }
    };

    public Set<Role> getRoles() {
        return roles;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return getName() +
            (roles.isEmpty() ? "" : " with roles: " + roles);
    }
}
