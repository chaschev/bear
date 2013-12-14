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
import bear.context.AbstractContext;
import bear.context.HavingContext;
import bear.core.Bear;
import bear.core.BearScriptPhase;
import bear.core.GlobalTaskRunner;
import bear.core.SessionContext;
import bear.main.phaser.ComputingGrid;
import bear.main.phaser.Phase;
import bear.main.phaser.PhaseParty;
import bear.session.DynamicVariable;
import bear.vcs.CommandLineResult;
import chaschev.util.Exceptions;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static bear.session.Variables.newVar;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Task<TASK_DEF extends TaskDef> extends HavingContext<Task<TaskDef>, SessionContext> {
    private Dependencies dependencies = new Dependencies();

    private final String id = SessionContext.randomId();

    protected TaskCallable taskCallable;
    protected final TaskContext<TASK_DEF> taskContext;

    public Task(TaskContext<TASK_DEF> taskContext, TaskCallable taskCallable) {
        super(taskContext.$);
        taskContext.me = this;
        this.taskContext = taskContext;
        this.taskCallable = taskCallable;

        setExecutionContext(new TaskExecutionContext($, this));
    }

    public Task(Task parent, TaskCallable<TaskDef> taskCallable) {
        super(parent.$);

        this.taskContext = parent.taskContext.dup(this, null, parent);
        this.taskCallable = taskCallable;

        setExecutionContext(new TaskExecutionContext($, this));
    }

    public Task(Task parent, TASK_DEF definition, SessionContext $) {
        super($);

        this.taskContext = new TaskContext<TASK_DEF>(this, parent, $, definition);

        setExecutionContext(new TaskExecutionContext($, this));
    }

    public TaskResult run(SessionTaskRunner runner, Object input) {
        if (getParent() != null) {
            getParent().getExecutionContext().addNewSubTask(this);
        }

        TaskResult result = null;

        try {
            if (taskCallable == null) {
                result = exec(runner, input);
            } else {
                result = taskCallable.call($, this, input);
            }
        } catch (Exception e) {
            result = new TaskResult(e);
        } finally {
            getExecutionContext().taskResult = result;

            if (getParent() != null) {
                getParent().getExecutionContext().onEndSubTask(this, result);
            }
        }

        return result;
    }

    protected TaskResult exec(SessionTaskRunner runner, Object input) {
        throw new UnsupportedOperationException("todo: implement or use nop() task or set callable!");
    }

    private static final Task<TaskDef> NOP_TASK = new Task<TaskDef>(null, null, null) {
        @Override
        protected TaskResult exec(SessionTaskRunner runner, Object input) {
            return TaskResult.OK;
        }
    };

    public static Task<TaskDef> nop() {
        return NOP_TASK;
    }

    public Dependencies getDependencies() {
        return dependencies;
    }

    protected void onRollback() {
        //todo use it
    }

    @Override
    public String toString() {
        return getDefinition() == null ? getClass().getSimpleName() :

            getDefinition().getDisplayName();
    }

    public Task<TASK_DEF> addDependency(Dependency... dependencies) {
        this.dependencies.addDependencies(dependencies);
        return this;
    }

    public <T extends CommandLineResult> void onCommandExecutionStart(AbstractConsoleCommand<T> command) {
        getExecutionContext().addNewCommand(command);
    }

    public <T extends CommandLineResult> void onCommandExecutionEnd(AbstractConsoleCommand<T> command, T result) {
        getExecutionContext().onEndCommand(command, result);
    }

    @Nullable
    public Task<TaskDef> getParent() {
        return taskContext.parent;
    }

    public Task<TASK_DEF> setParent(Task parent) {
        taskContext.parent = parent;
        return this;
    }

    public boolean isRootTask() {
        Task<TaskDef> parent = getParent();
        return parent == null || parent.getDefinition() == TaskDef.ROOT;
    }

    public TaskExecutionContext getExecutionContext() {
        return taskContext.executionContext;
    }

    public void init(
        Phase<?, BearScriptPhase> phase,
        PhaseParty<SessionContext, BearScriptPhase> party, ComputingGrid<SessionContext, ?> grid, GlobalTaskRunner globalTaskRunner) {
        taskContext.phase = phase;
        taskContext.phaseParty = party;
        taskContext.grid = (ComputingGrid) party.grid;
        taskContext.globalRunner = globalTaskRunner;
        $ = party.getColumn();
        taskContext.executionContext.set$($);
        taskContext.$ = $;
    }

    public TASK_DEF getDefinition() {
        return taskContext.definition;
    }

    public void setDefinition(TASK_DEF definition) {
        taskContext.definition = definition;
    }

    public void setExecutionContext(TaskExecutionContext executionContext) {
        taskContext.executionContext = executionContext;
    }

    public String getId() {
        return id;
    }

    public Bear getBear() {
        return taskContext.bear;
    }

    public void setBear(Bear bear) {
        taskContext.bear = bear;
    }

    public SessionTaskRunner getRunner() {
        return taskContext.runner;
    }

    public void setRunner(SessionTaskRunner runner) {
        taskContext.runner = runner;
    }

    public ComputingGrid<SessionContext, BearScriptPhase> getGrid() {
        return taskContext.grid;
    }

    public void setGrid(ComputingGrid<SessionContext, BearScriptPhase> grid) {
        taskContext.grid = grid;
    }

    public PhaseParty<SessionContext, BearScriptPhase> getPhaseParty() {
        return taskContext.phaseParty;
    }

    public void setPhaseParty(PhaseParty<SessionContext, BearScriptPhase> phaseParty) {
        taskContext.phaseParty = phaseParty;
    }

    public Phase<?, BearScriptPhase> getPhase() {
        return taskContext.phase;
    }

    public void setPhase(Phase<?, BearScriptPhase> phase) {
        taskContext.phase = phase;
    }

    public GlobalTaskRunner getGlobalRunner() {
        return taskContext.globalRunner;
    }

    public <T> ListenableFuture<T> callOnce(Callable<T> callable) {
        return getPhase().callOnce(callable);
    }

    public <T> Phase<T, BearScriptPhase> getRelativePhase(int offset, Class<T> tClass) {
        return getPhase().getRelativePhase(offset, tClass);
    }

    public <T> T getPreviousResult(Class<T> tClass) {
        return getPreviousResult(1, tClass);
    }

    public <T> T getPreviousResult(int distance, Class<T> tClass) {
        try {
            Preconditions.checkArgument(distance > 0, "distance must be positive");
            return getGrid().cellAt(getPhase().getRowIndex() - distance, getPhaseParty().getIndex(), tClass).getFuture().get(0, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }

    public <T> List<ListenableFuture<T>> getRelativeFutures(int offset, Class<T> tClass) {
        return getGrid().phaseFutures(getPhase().getRowIndex() + offset, tClass);
    }

    public <T> ListenableFuture<List<T>> aggregateRelatively(int offset, Class<T> tClass) {
        return getGrid().aggregateSuccessful(getPhase().getRowIndex() + offset, tClass);
    }

    public void awaitOthers(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        aggregateRelatively(-1, Object.class).get(timeout, unit);
    }

    public static TaskCallable<TaskDef> awaitOthersCallable(final int timeout, final TimeUnit unit) {
        return awaitOthersCallable(newVar(timeout), unit);
    }

    public static TaskCallable<TaskDef> awaitOthersCallable(final DynamicVariable<Integer> timeout, final TimeUnit unit) {
        return new TaskCallable<TaskDef>() {
            @Override
            public TaskResult call(SessionContext $, Task<TaskDef> task, Object input) throws Exception {
                try {
                    task.awaitOthers($.var(timeout), unit);
                    return TaskResult.OK;
                } catch (TimeoutException e) {
                    return new TaskResult(new Exception("timeout while waiting for parties"));
                }
            }
        };
    }

    public Task<TASK_DEF> wire(AbstractContext $) {
        $.wire(taskContext);
        return this;
    }


}
