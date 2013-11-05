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

import bear.console.AbstractConsoleCommand;
import bear.core.SessionContext;
import bear.plugins.HavingContext;
import bear.task.exec.TaskExecutionContext;
import bear.vcs.CommandLineResult;

import javax.annotation.Nullable;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class Task<TASK_DEF extends TaskDef> extends HavingContext<Task<TaskDef>, SessionContext> {
    protected TASK_DEF definition;

    @Nullable
    protected Task parent;

    private Dependencies dependencies = new Dependencies();

    protected TaskExecutionContext executionContext;

    public final String id = SessionContext.randomId();

    public Task(Task parent, TASK_DEF definition, SessionContext $) {
        super($);

        this.parent = parent;
        this.definition = definition;

        executionContext = new TaskExecutionContext($);
    }

    public TaskResult run(TaskRunner runner){
        if(parent != null){
            parent.executionContext.onNewSubTask(this);
        }

        TaskResult result = exec(runner);

        executionContext.taskResult = result;

        if(parent != null){
            parent.executionContext.onEndSubTask(this, result);
        }

        return result;
    }

    protected abstract TaskResult exec(TaskRunner runner) ;

    private static final Task<TaskDef> NOP_TASK = new Task<TaskDef>(null, null, null) {
        @Override
        protected TaskResult exec(TaskRunner runner) {
            return TaskResult.OK;
        }
    };

    public static Task<TaskDef> nop() {
        return NOP_TASK;
    }

    public Dependencies getDependencies(){
        return dependencies;
    }

    protected void onRollback() {
        //todo use it
    }

    @Override
    public String toString() {
        return definition == null ? getClass().getSimpleName() :

            definition.getDisplayName();
    }

    public Task<TASK_DEF> addDependency(Dependency... dependencies) {
        this.dependencies.addDependencies(dependencies);
        return this;
    }

    public <T extends CommandLineResult> void onCommandExecutionStart(AbstractConsoleCommand<T> command) {
        executionContext.onNewCommand(command);
    }

    public <T extends CommandLineResult> void onCommandExecutionEnd(AbstractConsoleCommand<T> command, T result) {
        executionContext.onEndCommand(command, result);
    }

    @Nullable
    public Task<TaskDef> getParent() {
        return parent;
    }

    public Task<TASK_DEF> setParent(Task parent) {
        this.parent = parent;
        return this;
    }

    public boolean isRootTask(){
        return parent == null;
    }

    public TaskExecutionContext getExecutionContext() {
        return executionContext;
    }

}
