package bear.core;

import bear.main.event.NewPhaseConsoleEventToUI;
import bear.main.event.PhasePartyFinishedEventToUI;
import bear.main.phaser.Phase;
import bear.main.phaser.PhaseCallable;
import bear.main.phaser.PhaseParty;
import bear.plugins.Plugin;
import bear.session.Result;
import bear.task.*;
import chaschev.lang.MutableSupplier;
import chaschev.util.Exceptions;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static bear.core.SessionContext.ui;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GridBuilder {
    //globalRunner is inject on execution
    final MutableSupplier<GlobalTaskRunner> globalRunner = new MutableSupplier<GlobalTaskRunner>();

    List<Phase<TaskResult, BearScriptPhase<Object, TaskResult>>> phases = new ArrayList<Phase<TaskResult, BearScriptPhase<Object, TaskResult>>>();
    private Map<Object, Object> variables;

    protected BearProject project;
    private boolean shutdownAfterRun = true;

    public GridBuilder add(final TaskDef<Object, TaskResult> taskDef){
        _addTask(taskDef);

        return this;
    }

    public Phase<TaskResult, BearScriptPhase<Object, TaskResult>> addSingle(SingleTaskSupplier<Object, TaskResult> supplier){
        return _addTask(new TaskDef<Object, TaskResult>(supplier)).get(0);
    }

    public GridBuilder add(TaskCallable<Object, TaskResult> callable){
        return add(Tasks.newSingleSupplier(callable));
    }

    public GridBuilder add(SingleTaskSupplier<Object, TaskResult> supplier){
        _addTask(new TaskDef<Object, TaskResult>(supplier)).get(0);
        return this;
    }

    public Phase<TaskResult, BearScriptPhase<Object, TaskResult>> addSingleTask(final TaskDef<Object, TaskResult> taskDef){
        Preconditions.checkArgument(!taskDef.isMultitask(), "expecting a single task");

        return _addTask(taskDef).get(0);
    }

    public List<Phase<TaskResult, BearScriptPhase<Object, TaskResult>>> addMultitask(final TaskDef<Object, TaskResult> taskDef){
        Preconditions.checkArgument(taskDef.isMultitask(), "expecting a multi task");

        return _addTask(taskDef);
    }

    //this seems to be the heart of grid execution

    protected List<Phase<TaskResult, BearScriptPhase<Object, TaskResult>>> _addTask(final TaskDef<Object, TaskResult> taskDef){
        List<TaskDef<Object, TaskResult>> listOfDefs;

        if (taskDef.isMultitask()) {
            listOfDefs = taskDef.asList();
        } else {
            listOfDefs = Collections.singletonList(taskDef);
        }

        List<Phase<TaskResult, BearScriptPhase<Object, TaskResult>>> subPhases = new ArrayList<Phase<TaskResult, BearScriptPhase<Object, TaskResult>>>();

        //for multitasks verifications are applied only for the first task
        boolean isFirstCallable = true;

        for (final TaskDef<Object, TaskResult> def : listOfDefs) {

            final boolean isFirstCallableFinal = isFirstCallable;

            subPhases.add(new Phase<TaskResult, BearScriptPhase<Object, TaskResult>>(new BearScriptPhase<Object, TaskResult>(def, null, GlobalTaskRunner.createGroupDivider()),
                new Function<Integer, PhaseCallable<SessionContext, TaskResult, BearScriptPhase<Object, TaskResult>>>() {
                @Override
                public PhaseCallable<SessionContext, TaskResult, BearScriptPhase<Object, TaskResult>> apply(final Integer partyIndex) {

                    return new PhaseCallable<SessionContext, TaskResult, BearScriptPhase<Object, TaskResult>>() {
                        @Override
                        public TaskResult call(final PhaseParty<SessionContext, BearScriptPhase<Object, TaskResult>> party, int phaseIndex, final Phase<TaskResult, BearScriptPhase<Object, TaskResult>> phase) throws Exception {
                            final List<SessionContext> $s = globalRunner.get().getSessions();

                            // this is ugly and I know it
                            phase.getPhase().init($s);

                            final SessionContext $ = $s.get(partyIndex);

                            TaskResult result = TaskResult.OK;

                            try {
                                ui.info(new NewPhaseConsoleEventToUI($.getName(), $.id, phase.getPhase().id));

                                $.setThread(Thread.currentThread());

                                $.whenPhaseStarts(phase.getPhase(), globalRunner.get().getShellContext());

                                if (isFirstCallableFinal && $.var($.bear.verifyPlugins)) {
                                    DependencyResult r = new DependencyResult(Result.OK);

                                    for (Plugin<Task, TaskDef> plugin : $.getGlobal().getOrderedPlugins()) {
                                        r.join(plugin.checkPluginDependencies());

                                        //todo replace with insideInstallation? - no!
                                        if (!taskDef.isSetupTask()) {
                                            InstallationTaskDef<? extends InstallationTask> installTaskDef = plugin.getInstall();
                                            InstallationTask session = (InstallationTask) installTaskDef
                                                .singleTaskSupplier()
                                                .createNewSession($, $.currentTask, installTaskDef);

                                            Dependency dependency = session
                                                .asInstalledDependency();

                                            result = $.runner.runSession(dependency);

                                            r.join((DependencyResult) result);
                                        }
                                    }

                                    if (r.nok()) {
                                        throw PartyResultException.create(r, party, phase);
                                    }
                                }

                                $.runner.taskPreRun = new Function<Task<Object, TaskResult>, Task<Object, TaskResult>>() {
                                    @Override
                                    public Task<Object, TaskResult> apply(Task<Object, TaskResult> task) {
                                        task.init(phase, party, party.grid, globalRunner.get());

                                        return task;
                                    }
                                };

                                result = $.run(def);

                                if (!result.ok()) {
                                    throw PartyResultException.create(result, party, phase);
                                }

                                return $.runner.getMyLastResult();
                            } catch (PartyResultException e) {
                                throw e;
                            } catch (Throwable e) {
                                BearMain.logger.warn("", e);
                                result = new TaskResult(e);

                                $.executionContext.rootExecutionContext.getDefaultValue().taskResult = result;

                                throw Exceptions.runtime(e);
                            } finally {
                                try {
                                    long duration = System.currentTimeMillis() - phase.getPhase().startedAtMs;
                                    phase.getPhase().addArrival($, duration, result);
                                    $.executionContext.rootExecutionContext.fireExternalModification();

                                    BearMain.ui.info(new PhasePartyFinishedEventToUI($.getName(), duration, result));

                                    $.whenSessionComplete(globalRunner.get());
                                } catch (Exception e) {
                                    BearMain.logger.warn("", e);
                                }
                            }
                        }
                    };
                }
            }));

            isFirstCallable = false;
        }

        phases.addAll(subPhases);

        return subPhases;
    }

    public GridBuilder injectGlobalRunner(GlobalTaskRunner globalTaskRunner){
        globalRunner.setInstance(globalTaskRunner).makeFinal();
        return this;
    }

    public GridBuilder addAll(List<? extends TaskDef> taskList) {
        for (TaskDef<Object, TaskResult> def : taskList) {
            add(def);
        }

        return this;
    }

    public List<Phase<TaskResult, BearScriptPhase<Object, TaskResult>>> build() {
        List<Phase<TaskResult, BearScriptPhase<Object, TaskResult>>> r = phases;
//        phases = null;
        return r;
    }

    public GridBuilder withVars(Map<Object, Object> variables) {
        this.variables = Collections.unmodifiableMap(variables);

        return this;
    }

    protected BearMain bearMain;

    public void launchUI() {
        throw new UnsupportedOperationException("todo");
    }

    public GlobalTaskRunner runCli() {
        return run();
    }

    public GlobalTaskRunner run() {

        if(bearMain == null){
            try {
                bearMain = new BearMain(GlobalContext.getInstance(), null)
                    .configure();
              } catch (IOException e) {
                throw Exceptions.runtime(e);
            }
        }

        return BearMain.run(project, this, variables, shutdownAfterRun);
    }

    public void init(BearProject<?> project) {
        this.project = project;

    }

    public GridBuilder setShutdownAfterRun(boolean shutdownAfterRun) {
        this.shutdownAfterRun = shutdownAfterRun;
        return this;
    }
}
