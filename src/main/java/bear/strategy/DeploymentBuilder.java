package bear.strategy;

import bear.core.SessionContext;
import bear.task.Task;
import bear.task.TaskCallable;
import bear.task.TaskDef;
import bear.task.TaskResult;

import javax.annotation.Nullable;

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
    protected final WaitForServiceToStop waitForServiceToStop = new WaitForServiceToStop();
    protected final StartService startService = new StartService();
    protected final WaitForServiceToStart waitForServiceToStart = new WaitForServiceToStart();
    protected final WhenStarted whenStarted = new WhenStarted();

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

        public StopService $5_StopService() {
            return stopService;
        }
    }

    public class StopService extends DeploymentStep<StopService> {
        public UpdateLinks $6_UpdateLinks() {
            return updateLinks;
        }

        public WaitForServiceToStop $7_WaitForServiceToStop() {
            return waitForServiceToStop;
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

        public WaitForServiceToStop $7_WaitForServiceToStop() {
            return waitForServiceToStop;
        }

        public UpdateLinks addSymlink(SymlinkEntry symlinkEntry) {
            symlinks.add(symlinkEntry);
            return this;
        }
    }

    public class WaitForServiceToStop extends DeploymentStep<WaitForServiceToStop> {
        boolean waitAllParties;  // implement later
        boolean doWait;

        public StartService $8_StartService() {
            return startService;
        }
    }

    public class StartService extends DeploymentStep<StartService> {
        public WaitForServiceToStart WaitForServiceToStart_9() {
            return waitForServiceToStart;
        }

        public WhenStarted $10_WhenStarted() {
            return whenStarted;
        }

        public DeploymentBuilder done() {
            return DeploymentBuilder.this;
        }
    }

    public class WaitForServiceToStart extends DeploymentStep<WaitForServiceToStart> {
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

    public TaskDef<Task> build(){
        return null;
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
