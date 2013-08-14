package cap4j.task;

import cap4j.core.CapConstants;
import cap4j.core.GlobalContext;
import cap4j.session.Result;
import com.google.common.base.Preconditions;


/**
 * User: ACHASCHEV
 * Date: 7/24/13
 */
public class Tasks {
    CapConstants cap;
    GlobalContext global;

    public Tasks(GlobalContext global) {
        this.global = global;
        this.cap = global.cap;
        Preconditions.checkNotNull(cap);
    }

    public final Task<TaskResult> restartApp = new Task<TaskResult>() {

    };

    public final Task<TaskResult> deploy = new Task<TaskResult>("deploy") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return new TaskResult(runner.run(
                update,
                restartApp));
        }
    };

    public final Task<TaskResult> setup = new Task<TaskResult>("setup") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            final String appLogs = var(cap.appLogsPath);
            final String[] dirs = {
                var(cap.deployTo), var(cap.releasesPath), var(cap.vcsCheckoutPath),
                appLogs
            };

            system.sudo().mkdirs(dirs);

            final String sshUser = var(cap.sshUsername);
            final String appUser = var(cap.appUsername);

            system.sudo().chown(sshUser + "." + sshUser, true, dirs);
            system.sudo().chmod("g+w", true, dirs);

            if(!appUser.equals(sshUser)){
                system.sudo().chown(appUser + "." + appUser, true, appLogs);
            }

            return new TaskResult(Result.OK);
        }
    };

    public final Task<TaskResult> update = new Task<TaskResult>("update") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return new TaskResult(runner.run(new TransactionTask(
                updateCode,
                createSymlink
            )));
        }
    };

    public final Task<TaskResult> updateCode = new Task<TaskResult>("updateCode") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return new TaskResult(
                Result.and(var(cap.newStrategy).deploy(),
                    runner.run(finalizeTouchCode)
                )
                );
        }

        @Override
        protected void onRollback() {
            system.rm(var(cap.releasesPath));
        }
    };


    public final Task<TaskResult> finalizeTouchCode = new Task<TaskResult>("finalizeTouchCode") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            system.chmod("g+w", true, var(cap.getLatestReleasePath));

            //new SimpleDateFormat("yyyyMMdd.HHmm.ss")
            return new TaskResult(Result.OK);
        }
    };

    public final Task<TaskResult> createSymlink = new Task<TaskResult>("createSymlink") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return new TaskResult(system.link(var(cap.getLatestReleasePath), var(cap.currentPath)));
        }

        @Override
        protected void onRollback() {
            system.link(var(cap.getPreviousReleasePath), var(cap.currentPath));
        }
    };
}
