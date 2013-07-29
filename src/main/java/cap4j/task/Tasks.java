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
            final String[] dirs = {varS(deployTo), varS(releasesPath)};

            system.mkdirs(dirs);
            system.chmod("g+w", true, dirs);

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
            return new TaskResult(runner.run(finalizeTouchCode));
        }

        @Override
        protected void onRollback() {
            system.rm(varS(releasesPath));
        }
    };


    public static final Task<TaskResult> finalizeTouchCode = new Task<TaskResult>("finalizeTouchCode") {
        @Override
        protected TaskResult run(TaskRunner runner) {
            system.chmod("g+w", true, varS(latestRelease));

            //new SimpleDateFormat("yyyyMMdd.HHmm.ss")
            return new TaskResult(Result.OK);
        }
    };

    public static final Task<TaskResult> createSymlink = new Task<TaskResult>("createSymlink") {
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
