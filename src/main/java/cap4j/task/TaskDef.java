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

import cap4j.core.*;
import com.google.common.collect.Sets;

import java.util.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class TaskDef<TASK extends Task> {

    String name;
    String description;

    private boolean setupTask;

    Set<Role> roles = new HashSet<Role>();

//    protected transient SessionContext $;

//    protected SystemEnvironment sys;

    List<TaskDef> beforeTasks = new ArrayList<TaskDef>();
    List<TaskDef> afterTasks = new ArrayList<TaskDef>();
    List<TaskDef> dependsOnTasks = new ArrayList<TaskDef>();

    public TaskDef() {
    }

    protected TaskDef(String name) {
        this.name = name;
    }

    protected TaskDef(String name, SessionContext $) {
        this.name = name;
    }

    public abstract TASK newSession(SessionContext $);

    public boolean hasRole(Set<Role> roles) {
        return !Sets.intersection(this.roles, roles).isEmpty();
    }

    public Task depends(Task... tasks) {
        Collections.addAll((List) dependsOnTasks, tasks);

        return this;
    }

    protected void defineVars(Console console) {

    }

    public TaskDef<T> addBeforeTask(Task task) {
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
}
