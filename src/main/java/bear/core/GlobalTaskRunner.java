package bear.core;

import bear.console.GroupDivider;
import bear.main.event.GlobalStatusEventToUI;
import bear.main.event.NewPhaseConsoleEventToUI;
import bear.main.event.NoticeEventToUI;
import bear.main.event.TaskConsoleEventToUI;
import bear.main.phaser.ComputingGrid;
import bear.main.phaser.Phase;
import bear.main.phaser.PhaseParty;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.TaskResult;
import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static bear.core.SessionContext.ui;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GlobalTaskRunner {
    private static final Logger logger = LoggerFactory.getLogger(GlobalTaskRunner.class);

    private final Bear bear;
    private final IBearSettings bearSettings;

    List<TaskDef<Task>> taskDefs;
    private final List<SessionContext> $s;

    ComputingGrid<SessionContext, BearScriptPhase> grid;

    private final long startedAtMs = System.currentTimeMillis();
    private final GlobalContext global;
    private final BearScript2.ShellSessionContext shellContext;

    public final DynamicVariable<Stats> stats;
    public final DynamicVariable<AtomicInteger> arrivedCount = Variables.newVar(new AtomicInteger(0));

    public GlobalTaskRunner(final GlobalContext global, List<Phase<TaskResult, BearScriptPhase>> phaseList, final PreparationResult preparationResult, final BearScript2.ShellSessionContext shellContext) {
        this.global = global;
        this.shellContext = shellContext;
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

        grid = new ComputingGrid<SessionContext, BearScriptPhase>(phaseList, $s);

        grid.setPhaseEnterListener(new ComputingGrid.PartyListener<BearScriptPhase, SessionContext>() {
            @Override
            public void handle(Phase<?, BearScriptPhase> phase, PhaseParty<SessionContext, BearScriptPhase> party) {
                ui.info(new NewPhaseConsoleEventToUI("shell", shellContext.sessionId, phase.getPhase().id));
                ui.info(new TaskConsoleEventToUI("shell", "step " + phase.getName() + "(" + phase.getPhase().id + ")", phase.getPhase().id)
                     .setId(phase.getPhase().id)
                    .setParentId(shellContext.sessionId)
                );
            }
        });

        grid.setPartyFinishListener(new ComputingGrid.PartyListener<BearScriptPhase, SessionContext>() {
            @Override
            public void handle(Phase<?, BearScriptPhase> phase, PhaseParty<SessionContext, BearScriptPhase> party) {
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
            public void run(int failedParties, int okParties) {
                if(failedParties > 0){
                    SessionContext.ui.error(new NoticeEventToUI("All parties arrived", failedParties + " errors"));
                }else{
                    SessionContext.ui.fatal(new NoticeEventToUI("All parties arrived.", null));
                }
            }
        });
    }

    static GroupDivider<SessionContext> createGroupDivider() {
        return new GroupDivider<SessionContext>(Stage.SESSION_ID, new Function<SessionContext, String>() {
            public String apply(SessionContext $) {
                DynamicVariable<Task> task = $.getExecutionContext().currentTask;
                return task.isUndefined() ? null : task.getDefaultValue().getId();
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

    public ComputingGrid<SessionContext, BearScriptPhase> getGrid() {
        return grid;
    }

    public List<SessionContext> getSessions() {
        return $s;
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
    }

    public GlobalContext getGlobal() {
        return global;
    }

    public long getStartedAtMs() {
        return startedAtMs;
    }

    public IBearSettings getBearSettings() {
        return bearSettings;
    }

    public BearScript2.ShellSessionContext getShellContext() {
        return shellContext;
    }
}
