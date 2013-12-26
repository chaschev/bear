package bear.core;

import bear.console.GroupDivider;
import bear.main.event.GlobalStatusEventToUI;
import bear.main.event.NewPhaseConsoleEventToUI;
import bear.main.event.NoticeEventToUI;
import bear.main.event.TaskConsoleEventToUI;
import bear.main.phaser.ComputingGrid;
import bear.main.phaser.Phase;
import bear.main.phaser.PhaseParty;
import bear.main.phaser.SettableFuture;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.TaskResult;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static bear.core.SessionContext.ui;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GlobalTaskRunner {
    private static final Logger logger = LoggerFactory.getLogger(GlobalTaskRunner.class);

    private final Bear bear;
    private final BearProject bearSettings;

    List<TaskDef<Object, TaskResult>> taskDefs;
    private final List<SessionContext> $s;

    ComputingGrid<SessionContext, BearScriptPhase<Object, TaskResult>> grid;

    private final long startedAtMs = System.currentTimeMillis();
    private final GlobalContext global;
    private final BearScriptRunner.ShellSessionContext shellContext;

    private final CountDownLatch finishedLatch = new CountDownLatch(1);

    ComputingGrid.WhenAllFinished whenAllFinished;

    public final DynamicVariable<Stats> stats;
    public final DynamicVariable<AtomicInteger> arrivedCount = Variables.newVar(new AtomicInteger(0));

    public GlobalTaskRunner(final GlobalContext global, List<Phase<TaskResult, BearScriptPhase<Object, TaskResult>>> phaseList, final PreparationResult preparationResult) {
        this.global = global;
        this.shellContext = new BearScriptRunner.ShellSessionContext();
        this.bear = global.bear;
        this.$s = preparationResult.getSessions();
        this.bearSettings = preparationResult.bearSettings;

        stats = Variables.dynamic(Stats.class)
            .defaultTo(new Stats($s.size(), TaskDef.EMPTY));

        stats.addListener(new DynamicVariable.ChangeListener<Stats>() {
            @Override
            public void changedValue(DynamicVariable<Stats> var, Stats oldValue, Stats newValue) {
                ui.info(new GlobalStatusEventToUI(newValue));
            }
        });

        grid = new ComputingGrid<SessionContext, BearScriptPhase<Object, TaskResult>>(phaseList, $s);

        grid.setPhaseEnterListener(new ComputingGrid.PartyListener<BearScriptPhase<Object, TaskResult>, SessionContext>() {
            @Override
            public void handle(Phase<?, BearScriptPhase<Object, TaskResult>> phase, PhaseParty<SessionContext, BearScriptPhase<Object, TaskResult>> party) {
                ui.info(new NewPhaseConsoleEventToUI("shell", shellContext.sessionId, phase.getPhase().id));
                ui.info(new TaskConsoleEventToUI("shell", "step " + phase.getName() + "(" + phase.getPhase().id + ")", phase.getPhase().id)
                     .setId(phase.getPhase().id)
                    .setParentId(shellContext.sessionId)
                );
            }
        });

        grid.setPartyFinishListener(new ComputingGrid.PartyListener<BearScriptPhase<Object, TaskResult>, SessionContext>() {
            @Override
            public void handle(Phase<?, BearScriptPhase<Object, TaskResult>> phase, PhaseParty<SessionContext, BearScriptPhase<Object, TaskResult>> party) {
                String name = phase.getPhase().getName();
                if(party.failed()){
                    SessionContext.ui.error(new NoticeEventToUI(
                        party.getColumn().getName() +
                            ": Party Failed", "Phase " + name + "(" + Throwables.getRootCause(party.getException()).toString() + ")"));
                }else{
                    SessionContext.ui.fatal(new NoticeEventToUI(
                        party.getColumn().getName(), "Party Finished"));
                }
            }
        });

        grid.setWhenAllFinished(new ComputingGrid.WhenAllFinished() {
            @Override
            public void run(final int failedParties, final int okParties) {
                try {

                    if (failedParties > 0) {
                        SessionContext.ui.error(new NoticeEventToUI("All parties arrived", failedParties + " errors"));
                    } else {
                        SessionContext.ui.fatal(new NoticeEventToUI(null, "All parties arrived"));
                    }

                    if (whenAllFinished != null) {
                        whenAllFinished.run(failedParties, okParties);
                    }

                } finally {
                    finishedLatch.countDown();
                }
            }
        });
    }

    static GroupDivider<SessionContext> createGroupDivider() {
        return new GroupDivider<SessionContext>(Stage.SESSION_ID, new Function<SessionContext, String>() {
            public String apply(SessionContext $) {
                DynamicVariable<Task> task = $.getExecutionContext().currentTask;
                return task.isUndefined() || task.getDefaultValue() == null ? null : task.getDefaultValue().getId();
            }
        }, new Function<SessionContext, String>() {
            @Override
            public String apply(SessionContext $) {
                return $.getExecutionContext().phaseText.getDefaultValue().toString();
            }
        });
    }

    public void startParties(ListeningExecutorService localExecutor) {
        grid.startParties(localExecutor);

        arrivedCount.addListener(new DynamicVariable.ChangeListener<AtomicInteger>() {
            @Override
            public void changedValue(DynamicVariable<AtomicInteger> var, AtomicInteger oldValue, AtomicInteger newValue) {
                if (newValue.get() == $s.size()) {
                    logger.info("finally home. removing stage from global scope");

                    global.currentGlobalRunner = null;

                    global.removeConst(bear.internalInteractiveRun);
                }
            }
        });
    }

    public ComputingGrid<SessionContext, BearScriptPhase<Object, TaskResult>> getGrid() {
        return grid;
    }

    public List<SessionContext> getSessions() {
        return $s;
    }

    public void throwIfAnyFailed() {
        if(stats.getDefaultValue().partiesFailed > 0){
            throw new RuntimeException("there are failed parties");
        }
    }

    public static class Stats{
        public final AtomicInteger partiesArrived = new AtomicInteger();
        public final AtomicInteger partiesOk = new AtomicInteger();
        public int partiesPending;
        public int partiesFailed = 0;
        public final AtomicInteger partiesCount;
        protected TaskDef rootTask;

        public Stats(int count, TaskDef rootTask) {
            partiesPending = count;
            this.rootTask = rootTask;
            partiesCount = new AtomicInteger(count);
        }

        public void addArrival(boolean isOk) {
            partiesArrived.incrementAndGet();
            partiesPending = partiesCount.get() - partiesArrived.get();

            if(isOk){
                partiesOk.incrementAndGet();
            }

            partiesFailed = partiesArrived.get() - partiesOk.get();
        }

        public String getRootTask() {
            return rootTask.getName();
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Stats{");
            sb.append("partiesArrived=").append(partiesArrived);
            sb.append(", partiesOk=").append(partiesOk);
            sb.append(", partiesPending=").append(partiesPending);
            sb.append(", partiesFailed=").append(partiesFailed);
            sb.append(", partiesCount=").append(partiesCount);
            sb.append('}');
            return sb.toString();
        }
    }

    public GlobalContext getGlobal() {
        return global;
    }

    public long getStartedAtMs() {
        return startedAtMs;
    }

    public BearProject getBearSettings() {
        return bearSettings;
    }

    public BearScriptRunner.ShellSessionContext getShellContext() {
        return shellContext;
    }

    public CountDownLatch getFinishedLatch() {
        return finishedLatch;
    }

    public GlobalTaskRunner whenAllFinished(ComputingGrid.WhenAllFinished whenAllFinished) {
        this.whenAllFinished = whenAllFinished;
        return this;
    }

    /**
     * @return Absent if the future was not found in the grid by the names provided.
     */
    public Optional<SettableFuture<TaskResult>> future(String taskDefName, String sessionName){
        return grid.future(taskDefName, sessionName, TaskResult.class);
    }
}
