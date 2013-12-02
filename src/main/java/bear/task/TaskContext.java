package bear.task;

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
public class TaskContext<TASK_DEF extends TaskDef> {
    Task me;

    @Nullable
    Task parent;
    SessionContext $;
    TASK_DEF definition;
    SessionTaskRunner runner;
    ComputingGrid<SessionContext, BearScriptPhase> grid;
    Phase<?, BearScriptPhase> phase;
    Bear bear;
    PhaseParty<SessionContext, BearScriptPhase> phaseParty;

    TaskExecutionContext executionContext;
    GlobalTaskRunner globalRunner;

    public TaskContext(Task me, Task parent, SessionContext $) {
        this.me = me;
        this.parent = parent;
        this.$ = $;
    }

    public TaskContext(Task me, Task parent, SessionContext $, TASK_DEF definition) {
        this.me = me;
        this.parent = parent;
        this.$ = $;
        this.definition = definition;
    }

    public TaskContext(Task parent, SessionContext $, SessionTaskRunner runner, Task me, TASK_DEF definition, ComputingGrid<SessionContext, BearScriptPhase> grid, Phase<?, BearScriptPhase> phase, Bear bear, PhaseParty<SessionContext, BearScriptPhase> phaseParty, TaskExecutionContext executionContext) {
        this.parent = parent;
        this.$ = $;
        this.runner = runner;
        this.me = me;
        this.definition = definition;
        this.grid = grid;
        this.phase = phase;
        this.bear = bear;
        this.phaseParty = phaseParty;
        this.executionContext = executionContext;
    }
    
    public TaskContext<TASK_DEF> dup(Task me){
        return new TaskContext<TASK_DEF>(
            parent, $, runner, me, definition, grid, phase, bear, phaseParty, executionContext
        );
    }

    public TaskContext<TASK_DEF> dup(Task me, TaskDef def, Task parent){
        TaskContext r = dup(me);

        r.parent = parent;
        r.definition = def;

        return r;
    }

    public GlobalTaskRunner getGlobalRunner() {
        return globalRunner;
    }
}
