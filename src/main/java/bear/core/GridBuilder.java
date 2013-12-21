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

    List<Phase<TaskResult, BearScriptPhase>> phases = new ArrayList<Phase<TaskResult, BearScriptPhase>>();
    private Map<Object, Object> variables;

    protected BearProject project;

    public GridBuilder add(final TaskDef<Task> taskDef){
        _addTask(taskDef);

        return this;
    }

    public Phase<TaskResult, BearScriptPhase> addSingle(SingleTaskSupplier<Task> supplier){
        return _addTask(new TaskDef<Task>(supplier)).get(0);
    }

    public GridBuilder add(TaskCallable<TaskDef> callable){
        return add(Tasks.newSingleTask(callable));
    }

    public GridBuilder add(SingleTaskSupplier<Task> supplier){
        _addTask(new TaskDef<Task>(supplier)).get(0);
        return this;
    }

    public Phase<TaskResult, BearScriptPhase> addSingleTask(final TaskDef<Task> taskDef){
        Preconditions.checkArgument(!taskDef.isMultitask(), "expecting a single task");

        return _addTask(taskDef).get(0);
    }

    public List<Phase<TaskResult, BearScriptPhase>> addMultitask(final TaskDef<Task> taskDef){
        Preconditions.checkArgument(taskDef.isMultitask(), "expecting a multi task");

        return _addTask(taskDef);
    }

    protected List<Phase<TaskResult, BearScriptPhase>> _addTask(final TaskDef<Task> taskDef){
        List<TaskDef<Task>> listOfDefs;

        if (taskDef.isMultitask()) {
            listOfDefs = taskDef.asList();
        } else {
            listOfDefs = Collections.singletonList(taskDef);
        }

        List<Phase<TaskResult, BearScriptPhase>> subPhases = new ArrayList<Phase<TaskResult, BearScriptPhase>>();

        //for multitasks verifications are applied only for the first task
        boolean isFirstCallable = true;

        for (final TaskDef<Task> def : listOfDefs) {

            final boolean isFirstCallableFinal = isFirstCallable;

            subPhases.add(new Phase<TaskResult, BearScriptPhase>(new BearScriptPhase(taskDef, null, GlobalTaskRunner.createGroupDivider()),
                new Function<Integer, PhaseCallable<SessionContext, TaskResult, BearScriptPhase>>() {
                @Override
                public PhaseCallable<SessionContext, TaskResult, BearScriptPhase> apply(final Integer partyIndex) {

                    return new PhaseCallable<SessionContext, TaskResult, BearScriptPhase>() {
                        @Override
                        public TaskResult call(final PhaseParty<SessionContext, BearScriptPhase> party, int phaseIndex, final Phase<?, BearScriptPhase> phase) throws Exception {
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

                                    for (Plugin<Task, TaskDef<?>> plugin : $.getGlobal().getGlobalPlugins()) {
                                        r.join(plugin.checkPluginDependencies());

                                        //todo replace with insideInstallation? - no!
                                        if (!taskDef.isSetupTask()) {
                                            InstallationTaskDef<? extends InstallationTask> installTask = plugin.getInstall();

                                            Dependency dependency = installTask
                                                .singleTaskSupplier()
                                                .createNewSession($, $.currentTask, (TaskDef) installTask)
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
                                        task.init(phase, party, party.grid, globalRunner.get());

                                        return task;
                                    }
                                };

                                result = $.run(def);

                                if (!result.ok()) {
                                    throw PartyResultException.create(result, party, phase.getName());
                                }

                                return result;
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
        for (TaskDef<Task> def : taskList) {
            add(def);
        }

        return this;
    }

    public List<Phase<TaskResult, BearScriptPhase>> build() {
        List<Phase<TaskResult, BearScriptPhase>> r = phases;
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

    public void runCli() {
        run();
    }

    public void run() {
        run(true);
    }

    public void run(boolean shutdown) {
        if(bearMain == null){
            try {
                bearMain = new BearMain(GlobalContext.getInstance(), null)
                    .configure();
              } catch (IOException e) {
                throw Exceptions.runtime(e);
            }
        }

        BearMain.run(project, this, variables, shutdown);
    }

    public void init(BearProject<?> project) {
        this.project = project;

    }


}
