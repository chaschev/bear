package bear.task;

import bear.context.HavingContext;
import bear.core.Bear;
import bear.core.BearScriptPhase;
import bear.core.GlobalTaskRunner;
import bear.core.SessionContext;
import bear.main.phaser.ComputingGrid;
import bear.main.phaser.Phase;
import bear.main.phaser.PhaseParty;

import javax.annotation.Nullable;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class TaskContext<I, O extends TaskResult> extends HavingContext<TaskContext<Object, TaskResult>, SessionContext> {
    Task me;

    @Nullable
    Task<Object, TaskResult> parent;

    TaskDef<I, O> definition;
    SessionRunner runner;
    ComputingGrid<SessionContext, BearScriptPhase<Object, TaskResult>> grid;
    Phase<O, BearScriptPhase<I, O>> phase;
    Bear bear;
    PhaseParty<SessionContext, BearScriptPhase<I, O>> phaseParty;

    TaskExecutionContext executionContext;
    GlobalTaskRunner globalRunner;

    I input;

    public TaskContext(Task me, Task parent, SessionContext $) {
        super($);
        this.me = me;
        this.parent = parent;
    }

    public TaskContext(Task me, Task parent, SessionContext $, TaskDef definition) {
        super($);
        this.me = me;
        this.parent = parent;
        this.definition = definition;
    }

    public TaskContext(
        Task parent, SessionContext $, SessionRunner runner,
        Task me, TaskDef definition,
        ComputingGrid<SessionContext, BearScriptPhase<Object, TaskResult>> grid,
        Phase<O, BearScriptPhase<I, O>> phase, Bear bear, PhaseParty<SessionContext, BearScriptPhase<I, O>> phaseParty, TaskExecutionContext executionContext) {

        super($);
        this.parent = parent;
        this.runner = runner;
        this.me = me;
        this.definition = definition;
        this.grid = grid;
        this.phase = phase;
        this.bear = bear;
        this.phaseParty = phaseParty;
        this.executionContext = executionContext;
    }

    public TaskContext<I, O> dup(Task me) {
        return new TaskContext<I, O>(
            parent, $, runner, me, definition, grid, phase, bear,
            phaseParty, executionContext
        );
    }

    public TaskContext<I, O> dup(Task me, TaskDef def, Task parent) {
        TaskContext<I, O> r = dup(me);

        r.parent = parent;
        r.definition = def;

        return r;
    }

    public GlobalTaskRunner getGlobalRunner() {
        return globalRunner;
    }
}
