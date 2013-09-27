package cap4j.task;

import cap4j.core.SessionContext;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public abstract class InstallationTaskDef<TASK extends InstallationTask> extends TaskDef<TASK>{
    public static final InstallationTaskDef<InstallationTask> EMPTY = new InstallationTaskDef<InstallationTask>() {
        @Override
        public InstallationTask newSession(SessionContext $) {
            return InstallationTask.nop();
        }
    };
}
