package cap4j.task;

import cap4j.CapConstants;
import cap4j.session.Result;

import static cap4j.CapConstants.*;

/**
 * User: ACHASCHEV
 * Date: 7/24/13
 */
public class Tasks {
    public static final Task<TaskResult> restartApp = new Task<TaskResult>() {

    };

    public static final Task<TaskResult> deploy = new Task<TaskResult>("deploy") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return new TaskResult(runner.run(
                update,
                restartApp));
        }
    };

    public static final Task<TaskResult> setup = new Task<TaskResult>("setup") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            final String appLogs = var(appLogsPath);
            final String[] dirs = {
                var(deployTo), var(releasesPath), var(vcsCheckoutPath),
                appLogs
            };

            system.sudo().mkdirs(dirs);

            final String sshUser = var(sshUsername);
            final String appUser = var(appUsername);

            system.sudo().chown(sshUser + "." + sshUser, true, dirs);
            system.sudo().chmod("g+w", true, dirs);

            if(!appUser.equals(sshUser)){
                system.sudo().chown(appUser + "." + appUser, true, appLogs);
            }

            return new TaskResult(Result.OK);
        }
    };

    public static final Task<TaskResult> update = new Task<TaskResult>("update") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return new TaskResult(runner.run(new TransactionTask(
                updateCode,
                createSymlink
            )));
        }
    };

    public static final Task<TaskResult> updateCode = new Task<TaskResult>("updateCode") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return new TaskResult(
                Result.and(var(newStrategy).deploy(),
                    runner.run(finalizeTouchCode)
                )
                );
        }

        @Override
        protected void onRollback() {
            system.rm(var(releasesPath));
        }
    };


    public static final Task<TaskResult> finalizeTouchCode = new Task<TaskResult>("finalizeTouchCode") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            system.chmod("g+w", true, var(CapConstants.getLatestReleasePath));

            //new SimpleDateFormat("yyyyMMdd.HHmm.ss")
            return new TaskResult(Result.OK);
        }
    };

    public static final Task<TaskResult> createSymlink = new Task<TaskResult>("createSymlink") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return new TaskResult(system.link(var(getLatestReleasePath), var(currentPath)));
        }

        @Override
        protected void onRollback() {
            system.link(var(getPreviousReleasePath), var(currentPath));
        }
    };
}
