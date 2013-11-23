package bear.core;

import bear.console.GroupDivider;
import bear.context.Cli;
import bear.main.event.*;
import bear.main.phaser.ComputingGrid;
import bear.main.phaser.Phase;
import bear.main.phaser.PhaseCallable;
import bear.main.phaser.PhaseParty;
import bear.plugins.Plugin;
import bear.session.DynamicVariable;
import bear.session.Result;
import bear.session.Variables;
import bear.task.*;
import bear.vcs.CommandLineResult;
import chaschev.util.Exceptions;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
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

    List<TaskDef<Task>> taskDefs;
    private final List<SessionContext> $s;

    ComputingGrid<SessionContext, BearScriptPhase> grid;

    private final long startedAtMs = System.currentTimeMillis();
    private final GlobalContext global;

    public final DynamicVariable<Stats> stats;
    public final DynamicVariable<AtomicInteger> arrivedCount = Variables.newVar(new AtomicInteger(0));

    public GlobalTaskRunner(final GlobalContext global, List<TaskDef<Task>> taskDefs, final List<SessionContext> $s, final BearScript2.ShellSessionContext shellContext) {
        this.global = global;
        this.bear = global.bear;
        this.taskDefs = taskDefs;
        this.$s = $s;

        stats = Variables.dynamic(Stats.class)
            .defaultTo(new Stats($s.size(), TaskDef.EMPTY));


        List<Phase<TaskResult, BearScriptPhase>> phaseList = Lists.newArrayList(Lists.transform(taskDefs, new Function<TaskDef, Phase<TaskResult, BearScriptPhase>>() {
            @Override
            public Phase<TaskResult, BearScriptPhase> apply(final TaskDef taskDef) {
                return new Phase<TaskResult, BearScriptPhase>(new BearScriptPhase(taskDef, null, createGroupDivider($s)), new Function<Integer, PhaseCallable<SessionContext, TaskResult, BearScriptPhase>>() {
                    @Override
                    public PhaseCallable<SessionContext, TaskResult, BearScriptPhase> apply(Integer partyIndex) {
                        final SessionContext $ = $s.get(partyIndex);

                        return new PhaseCallable<SessionContext, TaskResult, BearScriptPhase>() {
                            @Override
                            public TaskResult call(final PhaseParty<SessionContext, BearScriptPhase> party, int phaseIndex, final Phase<?, BearScriptPhase> phase) throws Exception {
                                TaskResult result = TaskResult.OK;

                                try {
                                    ui.info(new NewPhaseConsoleEventToUI($.getName(), $.id, phase.getPhase().id));

                                    $.setThread(Thread.currentThread());

                                    $.whenPhaseStarts(phase.getPhase(), shellContext);

                                    if ($.var($.bear.verifyPlugins)) {
                                        DependencyResult r = new DependencyResult(Result.OK);

                                        for (Plugin<Task, TaskDef<?>> plugin : global.getGlobalPlugins()) {
                                            r.join(plugin.checkPluginDependencies());

                                            if (!taskDef.isSetupTask()) {
                                                Dependency dependency = plugin.getInstall().newSession($, $.currentTask)
                                                    .asInstalledDependency();

                                                result = $.runner.runSession(dependency);

                                                r.join((DependencyResult) result);
                                            }
                                        }

                                        if (r.nok()) {
                                            throw PartyResultException.create(r, party, phase.getName());
                                        }
                                    }

                                    $.runner.taskPreRun = new Function<Task<TaskDef>, Task<TaskDef>>() {
                                        @Override
                                        public Task<TaskDef> apply(Task<TaskDef> task) {
                                            task.init(phase, party, party.grid);

                                            return task;
                                        }
                                    };

                                    result = $.runner.run(taskDef);

                                    if (!result.ok()) {
                                        throw PartyResultException.create(result, party, phase.getName());
                                    }

                                    return result;
                                } catch (PartyResultException e) {
                                    throw e;
                                } catch (Throwable e) {
                                    Cli.logger.warn("", e);
                                    final Throwable e1 = e;
                                    result = new TaskResult(e1);

                                    $.executionContext.rootExecutionContext.getDefaultValue().taskResult = new CommandLineResult(Result.ERROR, e.toString());

                                    throw Exceptions.runtime(e);
                                } finally {
                                    try {
                                        long duration = System.currentTimeMillis() - phase.getPhase().startedAtMs;
                                        phase.getPhase().addArrival($, duration, result);
                                        $.executionContext.rootExecutionContext.fireExternalModification();

                                        Cli.ui.info(new PhasePartyFinishedEventToUI($.getName(), duration, result));

                                        $.whenSessionComplete(GlobalTaskRunner.this);
                                    } catch (Exception e) {
                                        Cli.logger.warn("", e);
                                    }
                                }
                            }
                        };
                    }
                });
            }
        }));

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
                            ": Party Failed", "Phase " + name + "(" + party.getException().getCause().toString() + ")"));
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
                    SessionContext.ui.fatal(new NoticeEventToUI("All parties arrived", okParties + " ok!"));
                }
            }
        });
    }

    private static GroupDivider<SessionContext> createGroupDivider(List<SessionContext> $s) {
        return new GroupDivider<SessionContext>($s, Stage.SESSION_ID, new Function<SessionContext, String>() {
            public String apply(SessionContext $) {
                DynamicVariable<Task> task = $.getExecutionContext().currentTask;
                return task.isUndefined() ? null : task.getDefaultValue().id;
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

}
