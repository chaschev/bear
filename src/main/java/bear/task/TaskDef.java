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

import bear.context.Fun;
import bear.core.Role;
import bear.core.SessionContext;
import bear.session.DynamicVariable;
import bear.session.Variables;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;

import java.util.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */


public class TaskDef<TASK extends Task>{
    String name;
    public String description;

    private boolean setupTask;

    Set<Role> roles = new HashSet<Role>();

    List<TaskDef<Task>> beforeTasks = new ArrayList<TaskDef<Task>>();
    List<TaskDef<Task>> afterTasks = new ArrayList<TaskDef<Task>>();
    List<TaskDef<Task>> dependsOnTasks = new ArrayList<TaskDef<Task>>();
    private final MultitaskSupplier<TASK> multitaskSupplier;

    TaskDef<Task> rollback;

    private final SingleTaskSupplier<TASK> singleTaskSupplier;

    public TaskDef(TaskCallable<TaskDef> callable) {
        this(Tasks.<TASK>newSingleTask(callable));
    }

    public TaskDef(String name, TaskCallable<TaskDef> callable) {
        this(Tasks.<TASK>newSingleTask(callable));
        this.name = name;
    }

    public TaskDef(SingleTaskSupplier<TASK> singleTaskSupplier) {
        this(null, singleTaskSupplier);
    }

    public TaskDef(String name, SingleTaskSupplier<TASK> singleTaskSupplier) {
        this(null, singleTaskSupplier, null);
        this.name = name;
    }

    public TaskDef(String name, MultitaskSupplier multitaskSupplier) {
        this(name, null, multitaskSupplier);
    }

    public TaskDef(String name, SingleTaskSupplier<TASK> singleTaskSupplier, MultitaskSupplier<TASK> multitaskSupplier) {
        this.name = name;
        this.singleTaskSupplier = singleTaskSupplier;
        this.multitaskSupplier = multitaskSupplier;
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


    private TASK createNewSession(SessionContext $, final Task parent){
        Preconditions.checkArgument(!isMultitask(), "task is not multi");

        TASK task = singleTaskSupplier.createNewSession($, parent, this);

        task.wire($);

        return task;
    }

    public TaskResult runRollback(SessionRunner sessionRunner) {
        if(rollback != null){
            return sessionRunner.run(rollback);
        }
        return TaskResult.OK;
    }

    private List<TASK> createNewSessions(SessionContext $, final Task parent){
        Preconditions.checkArgument(isMultitask(), "task is multi");

        List<TASK> tasks = multitaskSupplier.createNewSessions($, parent);

        for (TASK task : tasks) {
            task.wire($);
        }

        return tasks;
    }

    public TaskDef<TASK> setName(String name) {
        this.name = name;
        return this;
    }

    public static interface TaskSupplier<TASK extends Task>{

    }

    public List<TASK> createNewSessionsAsList(SessionContext $, final Task parent){
        if(isMultitask()){
            return createNewSessions($, parent);
        }else{
            return Collections.singletonList(createNewSession($, parent));
        }
    }

    public SingleTaskSupplier<TASK> singleTaskSupplier(){
        Preconditions.checkArgument(!isMultitask(), "task is multi");

        return new SingleTaskSupplier<TASK>() {
            @Override
            public TASK createNewSession(SessionContext $, Task parent, TaskDef<TASK> def) {
                return TaskDef.this.createNewSession($, parent);
            }
        };
    }

    public MultitaskSupplier<TASK> multitaskSupplier(){
        Preconditions.checkArgument(isMultitask(), "task is not multi");

        return multitaskSupplier;
    }

    public boolean hasRole(Set<Role> roles) {
        return !Sets.intersection(this.roles, roles).isEmpty();
    }

    public TaskDef depends(Task<TaskDef>... tasks) {
        Collections.addAll((List) dependsOnTasks, tasks);

        return this;
    }

    public TaskDef<TASK> before(String name, TaskCallable<TaskDef> callable) {
        beforeTasks.add(new TaskDef<Task>(name, callable));
        return this;
    }

    public TaskDef<TASK> before(TaskCallable<TaskDef> callable) {
        beforeTasks.add(new TaskDef<Task>(callable));
        return this;
    }

    public TaskDef<TASK> addBeforeTask(TaskDef<Task> task) {
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

    public static final TaskDef EMPTY = new TaskDef<Task>("EMPTY", SingleTaskSupplier.NOP);

    public static final TaskDef ROOT = new TaskDef<Task>("ROOT", SingleTaskSupplier.NOP);

    public Set<Role> getRoles() {
        return roles;
    }

    public String getName() {
        return name == null ? "@" + hashCode() : name;
    }

    public String getDisplayName() {
        String name = this.name == null ? defaultName() : this.name;
        return name + (roles.isEmpty() ? "" : " with roles: " + roles);
    }

    public boolean isMultitask() {
        return multitaskSupplier != null;
    }


//    static AtomicInteger createdLists = new AtomicInteger();

    //the purpose is to lazy-initialize this factory
    //memoize is required because task is single, sub-taskdefs are created in a single thread...
    protected final Supplier<List<TaskDef<TASK>>> multiDefsSupplier = Suppliers.memoize(new Supplier<List<TaskDef<TASK>>>() {
        @Override
        public List<TaskDef<TASK>> get() {
            final TaskDef<TASK> enclosingTaskDef = TaskDef.this;

            MultitaskSupplier<TASK> multitaskSupplier = multitaskSupplier();
            List<TaskDef<TASK>> taskDefs = new ArrayList<TaskDef<TASK>>(multitaskSupplier.size());

            //attached to a task def, value memoized in a session
            final DynamicVariable<List<TASK>> taskListInASession = Variables.dynamic(new Fun<SessionContext, List<TASK>>() {
                @Override
                public List<TASK> apply(SessionContext $) {
                    // LoggerFactory.getLogger("log").info("created tasks: {}, $: {}, @{}", tasks, $.getName(), System.identityHashCode(list));

                    return enclosingTaskDef.multitaskSupplier().createNewSessions($, $.getCurrentTask());
                }
            })  .setName(enclosingTaskDef.getName())
                .memoizeIn(SessionContext.class);

            // "for this task def"
            // "in this session"
            // multitask is created once

            for (int i = 0; i < multitaskSupplier.size(); i++) {
                final int finalI = i;

                taskDefs.add(new TaskDef<TASK>(new SingleTaskSupplier<TASK>() {
                    @Override
                    public TASK createNewSession(SessionContext $, Task parent, TaskDef<TASK> def) {
                        Task.wrongThreadCheck($);
                        List<TASK> tasks = $.var(taskListInASession);

                        return tasks.get(finalI);
//                        return $.var(sessionMemMultiTask).createNewSessions($, parent).get(finalI);
                    }
                }));
            }

            return taskDefs;
        }
    });

    public List<TaskDef<TASK>> asList(){
        Preconditions.checkArgument(isMultitask(), "task is not multi");

        return multiDefsSupplier.get();
    }

    public TaskDef<TASK> onRollback(TaskDef<Task> rollback) {
        this.rollback = rollback;
        return this;
    }

    private String defaultName() {
        return classNameToTaskName(getClass().getSimpleName()).toString();
    }

    public TaskDef<Task> getRollback() {
        return rollback;
    }
}
