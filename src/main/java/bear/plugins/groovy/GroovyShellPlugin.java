package bear.plugins.groovy;

import bear.core.GlobalContext;
import bear.plugins.Plugin;
import bear.session.DynamicVariable;
import bear.session.Variables;
import bear.task.InstallationTask;
import bear.task.InstallationTaskDef;
import bear.task.Task;
import bear.task.TaskDef;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GroovyShellPlugin extends Plugin {
    public final DynamicVariable<Boolean> sendToHosts = Variables.newVar(Boolean.FALSE);

    public GroovyShellPlugin(GlobalContext global) {
        super(global);
        this.shell = global.wire(new GroovyShellMode(this));
    }

    public GroovyShellPlugin(GlobalContext global, TaskDef<? extends Task> taskDef) {
        super(global, taskDef);
    }

    @Override
    public GroovyShellMode getShell() {
        return (GroovyShellMode) this.shell;
    }

    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return InstallationTaskDef.EMPTY;
    }


}
