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
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;

import java.util.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */


public class TaskDef<I, O extends TaskResult>{
    String name;
    public String description;

    private boolean setupTask;

    Set<Role> roles = new HashSet<Role>();

    List<TaskDef<Object, TaskResult>> beforeTasks = new ArrayList<TaskDef<Object, TaskResult>>();
    List<TaskDef<Object, TaskResult>> afterTasks = new ArrayList<TaskDef<Object, TaskResult>>();
    List<TaskDef<Object, TaskResult>> dependsOnTasks = new ArrayList<TaskDef<Object, TaskResult>>();

    private final MultitaskSupplier multitaskSupplier;

    TaskDef<Object, TaskResult> rollback;

    private final SingleTaskSupplier<I, O> singleTaskSupplier;

    public TaskDef(TaskCallable<I, O> callable) {
        this(Tasks.<I, O>newSingleSupplier(callable));

        if (callable instanceof NamedCallable<?, ?>) {
            NamedCallable<I, O> namedCallable = (NamedCallable<I, O>) callable;
            setName(namedCallable.name);

        }
    }

    public TaskDef(String name, TaskCallable<I, O> callable) {
        this(Tasks.<I,O>newSingleSupplier(callable));
        this.name = name;
    }

    public TaskDef(SingleTaskSupplier<I, O> singleTaskSupplier) {
        this(null, singleTaskSupplier);

        if (singleTaskSupplier instanceof NamedSupplier<?, ?>) {
            NamedSupplier<?, ?> taskSupplier = (NamedSupplier<?, ?>) singleTaskSupplier;
            this.name = taskSupplier.name;
        }
    }

    public TaskDef(String name, SingleTaskSupplier<I, O> singleTaskSupplier) {
        this(null, singleTaskSupplier, null);
        this.name = name;
    }

    public TaskDef(String name, MultitaskSupplier multitaskSupplier) {
        this(name, null, multitaskSupplier);
    }

    public TaskDef(String name, SingleTaskSupplier<I, O> singleTaskSupplier, MultitaskSupplier multitaskSupplier) {
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


    private Task<I, O> createNewSession(SessionContext $, final Task parent){
        Preconditions.checkArgument(!isMultitask(), "task is not multi");

        Task task = singleTaskSupplier.createNewSession($, parent, this);

        task.wire($);

        return task;
    }

    public TaskResult runRollback(SessionRunner sessionRunner) {
        if(rollback != null){
            return sessionRunner.run(rollback);
        }
        return TaskResult.OK;
    }

    private List<Task> createNewSessions(SessionContext $, final Task parent){
        Preconditions.checkArgument(isMultitask(), "task is multi");

        List<TaskDef<Object, TaskResult>> defs = multitaskSupplier.getTaskDefs();
        List<Task> tasks = new ArrayList<Task>();

        for (TaskDef<Object, TaskResult> def : defs) {
            Task task = (Task) def.createNewSession($, parent).wire($);

            tasks.add(task);
        }

        return tasks;
    }

    public TaskDef<I, O> setName(String name) {
        this.name = name;
        return this;
    }

    public static interface TaskSupplier {

    }

    public List<Task<Object, TaskResult>> createNewSessionsAsList(SessionContext $, final Task parent){
        if(isMultitask()){
            return (List)createNewSessions($, parent);
        }else{
            return (List)Collections.singletonList(createNewSession($, parent));
        }
    }

    public SingleTaskSupplier<I, O> singleTaskSupplier(){
        Preconditions.checkArgument(!isMultitask(), "task is multi");

        return new SingleTaskSupplier<I, O>() {
            @Override
            public Task<I, O> createNewSession(SessionContext $, Task<Object, TaskResult> parent, TaskDef<I, O> def) {
                return TaskDef.this.createNewSession($, parent);
            }
        };
    }

    public MultitaskSupplier multitaskSupplier(){
        Preconditions.checkArgument(isMultitask(), "task is not multi");

        return multitaskSupplier;
    }

    public boolean hasRole(Set<Role> roles) {
        return !Sets.intersection(this.roles, roles).isEmpty();
    }

    public TaskDef depends(Task<Object, TaskResult>... tasks) {
        Collections.addAll((List) dependsOnTasks, tasks);

        return this;
    }

    public TaskDef<I, O> before(String name, TaskCallable<Object, TaskResult> callable) {
        beforeTasks.add(new TaskDef<Object, TaskResult>(name, callable));
        return this;
    }

    public TaskDef<I, O> before(TaskCallable<Object, TaskResult> callable) {
        beforeTasks.add(new TaskDef<Object, TaskResult>(callable));
        return this;
    }

    public TaskDef<I, O> addBeforeTask(TaskDef<Object, TaskResult> task) {
        beforeTasks.add(task);
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TaskDef{");
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

    public static final TaskDef EMPTY = new TaskDef<Object, TaskResult>("EMPTY", SingleTaskSupplier.NOP);

    public static final TaskDef ROOT = new TaskDef<Object, TaskResult>("ROOT", SingleTaskSupplier.NOP);

    public Set<Role> getRoles() {
        return roles;
    }

    public String getName() {
        return name == null ? getClass().getSimpleName() + "@" + hashCode() : name;
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
    protected final Supplier<List<TaskDef<Object, TaskResult>>> multiDefsSupplier = Suppliers.memoize(new Supplier<List<TaskDef<Object, TaskResult>>>() {
        @Override
        public List<TaskDef<Object, TaskResult>> get() {
            final TaskDef<I, O> enclosingTaskDef = TaskDef.this;

            MultitaskSupplier multitaskSupplier = multitaskSupplier();
            List<TaskDef<Object, TaskResult>> taskDefs = new ArrayList<TaskDef<Object, TaskResult>>(multitaskSupplier.size());

            //attached to a task def, value memoized in a session
//            final DynamicVariable<List<Task<Object, TaskResult>>> taskListInASession = Variables.dynamic(new Fun<SessionContext, List<Task<Object, TaskResult>>>() {
//                @Override
//                public List<Task<Object, TaskResult>> apply(SessionContext $) {
//                    // LoggerFactory.getLogger("log").info("created tasks: {}, $: {}, @{}", tasks, $.getName(), System.identityHashCode(list));
//
//                    return enclosingTaskDef.multitaskSupplier().createNewSessions($, $.getCurrentTask());
//                }
//            })  .setName(enclosingTaskDef.getName())
//                .memoizeIn(SessionContext.class);

            // "for this task def"
            // "in this session"
            // multitask is created once

//            for (int i = 0; i < multitaskSupplier.size(); i++) {
//                final int finalI = i;
//
//                final TaskDef<Object, TaskResult> taskDef = new TaskDef<Object, TaskResult>(new SingleTaskSupplier<Object, TaskResult>() {
//                    @Override
//                    public Task<Object, TaskResult> createNewSession(SessionContext $, Task<Object, TaskResult> parent, TaskDef<Object, TaskResult> def) {
//                        Task.wrongThreadCheck($);
//
//                        List<Task<Object, TaskResult>> tasks = $.var(taskListInASession);
//
//                        return tasks.get(finalI);
//                    }
//                });
//
//
//
//                taskDefs.add(taskDef);
//            }

            return taskDefs;
        }
    });

    public List<TaskDef<Object, TaskResult>> asList(){
        Preconditions.checkArgument(isMultitask(), "task is not multi");

        return (List) Collections.unmodifiableList(multitaskSupplier.getTaskDefs());
    }

    public TaskDef<I, O> onRollback(TaskDef<Object, TaskResult> rollback) {
        this.rollback = rollback;
        return this;
    }

    private String defaultName() {
        return classNameToTaskName(getClass().getSimpleName()).toString();
    }

    public TaskDef<Object, TaskResult> getRollback() {
        return rollback;
    }
}
