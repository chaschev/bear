package bear.core;

import bear.console.ConsolesDivider;
import bear.console.GroupDivider;
import bear.context.Cli;
import bear.main.BearFX;
import bear.main.event.GlobalStatusEventToUI;
import bear.main.event.PhaseFinishedEventToUI;
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
import chaschev.util.CatchyCallable;
import chaschev.util.Exceptions;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
            .defaultTo(new Stats($s.size(), null));


        List<Phase<TaskResult, BearScriptPhase>> phaseList = Lists.transform(taskDefs, new Function<TaskDef, Phase<TaskResult, BearScriptPhase>>() {
            @Override
            public Phase<TaskResult, BearScriptPhase> apply(final TaskDef taskDef) {
                return new Phase<TaskResult, BearScriptPhase>(new BearScriptPhase(null, createGroupDivider($s)), new Function<Integer, PhaseCallable<SessionContext, TaskResult, BearScriptPhase>>() {
                    @Override
                    public PhaseCallable<SessionContext, TaskResult, BearScriptPhase> apply(Integer partyIndex) {
                        final SessionContext $ = $s.get(partyIndex);

                        return new PhaseCallable<SessionContext, TaskResult, BearScriptPhase>() {
                            @Override
                            public TaskResult call(final PhaseParty<SessionContext> party, int phaseIndex, final Phase<?, BearScriptPhase> phase) throws Exception {
                                TaskResult result = TaskResult.OK;

                                try {
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
                                            throw new PartyResultException(r, party, phase.getName());
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

                                    if(!result.ok()){
                                        throw new PartyResultException(result, party, phase.getName());
                                    }

                                    return result;
                                } catch (PartyResultException e) {
                                    throw e;
                                }
                                catch (Throwable e){
                                    Cli.logger.warn("", e);
                                    result = new ExceptionResult(e);

                                    $.executionContext.rootExecutionContext.getDefaultValue().taskResult = new CommandLineResult(Result.ERROR, e.toString());

                                    throw Exceptions.runtime(e);
                                }
                                finally {
                                    try {
                                        long duration = System.currentTimeMillis() - phase.getPhase().startedAtMs;
                                        phase.getPhase().addArrival($, duration, result);
                                        $.executionContext.rootExecutionContext.fireExternalModification();

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
        });

        stats.addListener(new DynamicVariable.ChangeListener<Stats>() {
            @Override
            public void changedValue(DynamicVariable<Stats> var, Stats oldValue, Stats newValue) {
                ui.info(new GlobalStatusEventToUI(newValue));
            }
        });

        grid = new ComputingGrid<SessionContext, BearScriptPhase>(phaseList, $s);

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

    public static class BearScriptPhase {
        public final String id = SessionContext.randomId();
        TaskDef<Task> taskDef;
        final AtomicInteger partiesArrived = new AtomicInteger();
        public final AtomicInteger partiesOk = new AtomicInteger();

        final AtomicLong minimalOkDuration = new AtomicLong(-1);

        BearFX bearFX;

        GroupDivider<SessionContext> groupDivider;

        public final long startedAtMs = System.currentTimeMillis();

        final int partiesCount;

        public int partiesPending;
        public int partiesFailed = 0;

        public BearScriptPhase(BearFX bearFX, GroupDivider<SessionContext> groupDivider) {
            this.bearFX = bearFX;
            this.partiesCount = groupDivider.getEntries().size();
            this.groupDivider = groupDivider;
        }

        public String getName() {
            return taskDef.getName();
        }

        public String getDisplayName() {
            return taskDef.getDisplayName();
        }

        public void addArrival(SessionContext $, final long duration, TaskResult result) {
            groupDivider.addArrival($);

            if (result.ok()) {
                partiesOk.incrementAndGet();

                if (minimalOkDuration.compareAndSet(-1, duration)) {
                    $.getGlobal().scheduler.schedule(new CatchyCallable<Void>(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            boolean haveHangUpJobs;
                            boolean alreadyFinished;

                            if (partiesArrived.compareAndSet(partiesCount, -1)) {
                                alreadyFinished = false;
                                haveHangUpJobs = false;
                            } else {
                                if (partiesArrived.compareAndSet(-1, -1)) {
                                    haveHangUpJobs = false;
                                    alreadyFinished = true;
                                } else {
                                    alreadyFinished = false;
                                    haveHangUpJobs = true;
                                }
                            }

                            if (!alreadyFinished) {
                                sendPhaseResults(duration);
                            }
                            return null;
                        }
                    }), duration * 3, TimeUnit.MILLISECONDS);
                }
            }


            partiesArrived.incrementAndGet();

            partiesPending = partiesCount - partiesArrived.get();
            partiesFailed = partiesArrived.get() - partiesOk.get();

            if (partiesArrived.compareAndSet(partiesCount, -1)) {
                sendPhaseResults(duration);
            }
        }

        private void sendPhaseResults(long duration) {
            List<ConsolesDivider.EqualityGroup> groups = groupDivider.divideIntoGroups();

            bearFX.sendMessageToUI(
                //todo check this name is a one line desc
                new PhaseFinishedEventToUI(duration, groups, getName())
                    .setParentId(id));
        }
    }
}
