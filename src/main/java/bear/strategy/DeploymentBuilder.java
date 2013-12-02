package bear.strategy;

import bear.core.SessionContext;
import bear.task.*;
import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class DeploymentBuilder {
    protected final SetFilesLocation setFilesLocation = new SetFilesLocation();
    protected final CheckoutFiles checkoutFiles = new CheckoutFiles();
    protected final BuildAndCopy buildAndCopy = new BuildAndCopy();
    protected final WaitForPartiesBeforeRestart waitForPartiesBeforeRestart = new WaitForPartiesBeforeRestart();
    protected final StopService stopService = new StopService();
    protected final UpdateLinks updateLinks = new UpdateLinks();
    protected final AwaitServiceToStop awaitServiceToStop = new AwaitServiceToStop();
    protected final StartService startService = new StartService();
    protected final AwaitServiceToStart awaitServiceToStart = new AwaitServiceToStart();
    protected final WhenStarted whenStarted = new WhenStarted();

    protected final DeploymentStep[] deploymentSteps = new DeploymentStep[]{
        setFilesLocation, checkoutFiles, buildAndCopy, waitForPartiesBeforeRestart,
        stopService, updateLinks, awaitServiceToStop, startService, awaitServiceToStart,
        whenStarted
    };

    public abstract class DeploymentStep<SELF extends DeploymentStep> extends TaskDef<Task> {
        @Nullable
        protected TaskCallable<TaskDef> taskCallable;

        public SELF setTaskCallable(TaskCallable<TaskDef> taskCallable) {
            this.taskCallable = taskCallable;
            return self();
        }

        @Override
        protected Task newSession(SessionContext $, Task parent) {
            return taskCallable == null ? Task.nop() : new Task(parent, taskCallable);
        }

        @SuppressWarnings("unchecked")
        protected final SELF self(){
            return (SELF) this;
        }
    }

    public static interface DeployStepTask<T extends DeploymentStep>{
        TaskCallable<TaskDef> create(Task<TaskDef> parent, T deploymentStep);
    }

    public SetFilesLocation $1_SetFilesLocation(){
        return setFilesLocation;
    }

    public CheckoutFiles CheckoutFiles_2(){
        return checkoutFiles;
    }

    public CheckoutFiles CheckoutFiles_2(TaskCallable<TaskDef> t){
        return checkoutFiles.setTaskCallable(t);
    }

    public class SetFilesLocation extends DeploymentStep<SetFilesLocation> {
        public CheckoutFiles $2_CheckoutFiles(){
            return checkoutFiles;
        }

        public BuildAndCopy $3_BuildAndCopy(){
            return buildAndCopy;
        }

        public WaitForPartiesBeforeRestart $4_WaitForParties() {
            return waitForPartiesBeforeRestart;
        }
    }

    public class CheckoutFiles extends DeploymentStep<CheckoutFiles> {
        public BuildAndCopy BuildAndCopy_3(){
            return buildAndCopy;
        }

        public BuildAndCopy BuildAndCopy_3(TaskCallable<TaskDef> t){
            return buildAndCopy.setTaskCallable(t);
        }
    }

    public class BuildAndCopy extends DeploymentStep<BuildAndCopy> {
        public WaitForPartiesBeforeRestart $4_WaitForParties() {
            return waitForPartiesBeforeRestart;
        }

        public StopService StopService_5() {
            return stopService;
        }

        public StopService StopService_5(TaskCallable<TaskDef> t) {
            return stopService.setTaskCallable(t);
        }
    }

    public class WaitForPartiesBeforeRestart extends DeploymentStep<WaitForPartiesBeforeRestart> {
        boolean waitForParties;
        boolean failIfAnyoneFailed;

        long timeout = 1;
        TimeUnit timeUnit = TimeUnit.MINUTES;

        public StopService $5_StopService() {
            return stopService;
        }
    }

    public class StopService extends DeploymentStep<StopService> {
        public UpdateLinks $6_UpdateLinks() {
            return updateLinks;
        }

        public AwaitServiceToStop $7_WaitForServiceToStop() {
            return awaitServiceToStop;
        }

        public StartService StartService_8() {
            return startService;
        }

        public StartService StartService_8(TaskCallable<TaskDef> t) {
            return startService.setTaskCallable(t);
        }
    }

    public class UpdateLinks extends DeploymentStep<UpdateLinks> {
        protected Symlinks symlinks = new Symlinks();

        public AwaitServiceToStop $7_WaitForServiceToStop() {
            return awaitServiceToStop;
        }

        public UpdateLinks addSymlink(SymlinkEntry symlinkEntry) {
            symlinks.add(symlinkEntry);
            return this;
        }
    }

    public class AwaitServiceToStop extends DeploymentStep<AwaitServiceToStop> {
        boolean waitAllParties;  // implement later
        boolean doWait;

        public StartService $8_StartService() {
            return startService;
        }
    }

    public class StartService extends DeploymentStep<StartService> {
        public AwaitServiceToStart WaitForServiceToStart_9() {
            return awaitServiceToStart;
        }

        public WhenStarted $10_WhenStarted() {
            return whenStarted;
        }

        public DeploymentBuilder done() {
            return DeploymentBuilder.this;
        }
    }

    public class AwaitServiceToStart extends DeploymentStep<AwaitServiceToStart> {
        public WhenStarted $10_WhenStarted() {
            return whenStarted;
        }

        public DeploymentBuilder done() {
            return DeploymentBuilder.this;
        }
    }

    public class WhenStarted extends DeploymentStep<WhenStarted> {
        public DeploymentBuilder done() {
            return DeploymentBuilder.this;
        }
    }

    //todo each step could provide it's own implementation for the task
    public TaskDef<Task> build(){
        TaskDef<Task> taskDef = new TaskDef<Task>() {
            @Override
            protected List<Task> newSessions(SessionContext $, Task parent) {
                List<Task> tasks = new ArrayList<Task>();

                for (DeploymentStep<?> deploymentStep : deploymentSteps) {
                    Task<TaskDef> task;

                    if (deploymentStep instanceof WaitForPartiesBeforeRestart) {
                        task = waitForParties(parent, deploymentStep);
                    } else {
                        TaskCallable callable = deploymentStep.taskCallable;

                        if (deploymentStep instanceof AwaitServiceToStop) {
                            AwaitServiceToStop step = (AwaitServiceToStop) deploymentStep;
                            if (step.waitAllParties) {
                                throw new UnsupportedOperationException("todo - waitAllParties not yet supported!");
                            }
                        }

                        task = callable == null ? Task.nop() : new Task<TaskDef>(parent, callable);
                    }

                    Preconditions.checkNotNull(task, "nop() must be instead on null");

                    tasks.add(task);
                }

                return tasks;
            }
        };

        taskDef.setMultiTask(true);

        return taskDef;
    }

    private Task<TaskDef> waitForParties(Task parent, DeploymentStep<?> deploymentStep) {
        Task<TaskDef> task;
        final WaitForPartiesBeforeRestart step = (WaitForPartiesBeforeRestart) deploymentStep;

        TaskCallable<TaskDef> callable;

        if (step.waitForParties) {
            TaskCallable<TaskDef> awaitCallable = Task.awaitOthersCallable(step.timeout, step.timeUnit);

            if (deploymentStep.taskCallable == null) {
                callable = awaitCallable;
            } else {
                callable = Tasks.andThen(awaitCallable, deploymentStep.taskCallable);
            }
        } else {
            callable = deploymentStep.taskCallable;
        }

        task = callable == null ? Task.nop() : new Task<TaskDef>(parent, callable);
        return task;
    }

    public static void main(String[] args) {
        TaskDef<Task> taskDef = new DeploymentBuilder()
            .$1_SetFilesLocation()
            .setTaskCallable(new TaskCallable<TaskDef>() {
                @Override
                public TaskResult call(SessionContext $, Task<TaskDef> task, Object input) throws Exception {
                    throw new UnsupportedOperationException("todo .call");
                }
            })
            .$2_CheckoutFiles()
            .setTaskCallable(new TaskCallable<TaskDef>() {
                @Override
                public TaskResult call(SessionContext $, Task<TaskDef> task, Object input) throws Exception {
                    throw new UnsupportedOperationException("todo .call");
                }
            })
            .BuildAndCopy_3()
//            .$4_WaitForParties()
            .StopService_5()
            .$6_UpdateLinks()
            .$7_WaitForServiceToStop()
            .$8_StartService()
            .WaitForServiceToStart_9()
            .$10_WhenStarted()
            .done()
            .build();
    }
}
