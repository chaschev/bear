package cap4j.task;

import cap4j.session.Result;

import static cap4j.CapConstants.*;
import static cap4j.VariableName.latestRelease;
import static cap4j.VariableName.releasesPath;

/**
 * User: ACHASCHEV
 * Date: 7/24/13
 */
public class Tasks {
    public static final Task<TaskResult> RESTART_APP = new Task<TaskResult>() {

    };

    public static final Task<TaskResult> DEPLOY = new Task<TaskResult>() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return new TaskResult(runner.run(
                UPDATE,
                RESTART_APP));
        }
    };

    public static final Task<TaskResult> SETUP = new Task<TaskResult>() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            final String[] dirs = {varS(deployTo), varS(releasesPath)};

            system.mkdirs(dirs);
            system.chmod("g+w", true, dirs);

            return new TaskResult(Result.OK);
        }
    };

    public static final Task<TaskResult> UPDATE = new Task<TaskResult>() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return new TaskResult(runner.run(new TransactionTask(
                UPDATE_CODE,
                CREATE_SYMLINK
            )));
        }
    };

    public static final Task<TaskResult> UPDATE_CODE = new Task<TaskResult>() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return new TaskResult(runner.run(FINALIZE_TOUCH_FILES));
        }

        @Override
        protected void onRollback() {
            system.rm(varS(releasesPath));
        }
    };


    public static final Task<TaskResult> FINALIZE_TOUCH_FILES = new Task<TaskResult>() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            system.chmod("g+w", true, varS(latestRelease));

            //new SimpleDateFormat("yyyyMMdd.HHmm.ss")
            return new TaskResult(Result.OK);
        }
    };

    public static final Task<TaskResult> CREATE_SYMLINK = new Task<TaskResult>() {
        @Override
        protected TaskResult run(TaskRunner runner) {
            return new TaskResult(system.link(varS(getLatestReleasePath), varS(currentPath)));
        }

        @Override
        protected void onRollback() {
            system.link(varS(getPreviousReleasePath), varS(currentPath));
        }
    };
}
