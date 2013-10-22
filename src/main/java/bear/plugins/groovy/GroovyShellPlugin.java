package bear.plugins.groovy;

import bear.core.GlobalContext;
import bear.plugins.Plugin;
import bear.plugins.PluginShellMode;
import bear.task.InstallationTask;
import bear.task.InstallationTaskDef;
import bear.task.Task;
import bear.task.TaskDef;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GroovyShellPlugin extends Plugin {
    public GroovyShellPlugin(GlobalContext global) {
        super(global);
    }

    public GroovyShellPlugin(GlobalContext global, TaskDef<? extends Task> taskDef) {
        super(global, taskDef);
    }

    @Override
    public PluginShellMode getShell() {
        return null;
    }

    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return InstallationTaskDef.EMPTY;
    }
}
