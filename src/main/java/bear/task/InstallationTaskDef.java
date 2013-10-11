package bear.task;

import bear.core.SessionContext;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public abstract class InstallationTaskDef<TASK extends InstallationTask> extends TaskDef<TASK>{
    public static final InstallationTaskDef<InstallationTask> EMPTY = new InstallationTaskDef<InstallationTask>() {
        @Override
        public InstallationTask newSession(SessionContext $, final Task parent) {
            return InstallationTask.nop();
        }
    };
}
