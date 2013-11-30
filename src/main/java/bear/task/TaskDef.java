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

import bear.core.Role;
import bear.core.SessionContext;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import java.util.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class TaskDef<TASK extends Task> {
    protected boolean multiTask;

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

    private TASK createNewSession(SessionContext $, final Task parent){
        Preconditions.checkArgument(!multiTask, "task is not multi");

        TASK task = newSession($, parent);

        return $.wire(task);
    }

    private List<TASK> createNewSessions(SessionContext $, final Task parent){
        Preconditions.checkArgument(multiTask, "task is multi");

        List<TASK> tasks = newSessions($, parent);

        for (TASK task : tasks) {
            $.wire(task);
        }

        return tasks;
    }

    protected TASK newSession(SessionContext $, final Task parent){
        throw new UnsupportedOperationException("todo implement either this or newSessions");
    }

    protected List<TASK> newSessions(SessionContext $, final Task parent){
        throw new UnsupportedOperationException("todo implement either this or newSession");
    }

    public static interface SingleTask<TASK extends Task>{
        TASK createNewSession(SessionContext $, final Task parent);
    }

    public static interface MultiTask<TASK extends Task>{
        List<TASK> createNewSessions(SessionContext $, final Task parent);
    }

    public List<TASK> createNewSessionsAsList(SessionContext $, final Task parent){
        if(multiTask){
            return createNewSessions($, parent);
        }else{
            return Collections.singletonList(createNewSession($, parent));
        }
    }

    public SingleTask<TASK> singleTask(){
        Preconditions.checkArgument(!multiTask, "task is not multi");

        return new SingleTask<TASK>() {
            @Override
            public TASK createNewSession(SessionContext $, Task parent) {
                return TaskDef.this.createNewSession($, parent);
            }
        };
    }

    public MultiTask<TASK> multiTask(){
        Preconditions.checkArgument(multiTask, "task is multi");

        return new MultiTask<TASK>() {
            @Override
            public List<TASK> createNewSessions(SessionContext $, Task parent) {
                return TaskDef.this.createNewSessions($, parent);
            }
        };
    }

    public boolean hasRole(Set<Role> roles) {
        return !Sets.intersection(this.roles, roles).isEmpty();
    }

    public TaskDef depends(Task<TaskDef>... tasks) {
        Collections.addAll((List) dependsOnTasks, tasks);

        return this;
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

    public boolean isMultiTask() {
        return multiTask;
    }

    public void setMultiTask(boolean multiTask) {
        this.multiTask = multiTask;
    }


}
