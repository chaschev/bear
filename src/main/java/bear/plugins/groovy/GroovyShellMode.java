package bear.plugins.groovy;

import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.core.Stage;
import bear.main.BearCommandLineConfigurator;
import bear.plugins.CommandInterpreter;
import bear.plugins.PluginShellMode;
import bear.session.Result;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.TaskResult;
import bear.task.TaskRunner;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;

/**
 * There are two forms of application for this plugin. The first is to 'execute some bear code'. You would typically want to do this to inspect Bear's internals or to run some activity not covered by the console API.
 *
 * The second is to execute groovy code in a distributed fashion. This could be your custom task or a script written in dynamic Groovy.
 *
 * $ for the local shell is BearCommandLineConfigurator. It must be set during init.
 *
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GroovyShellMode extends PluginShellMode<GroovyShellPlugin> implements CommandInterpreter {
    private final Binding binding;
    private final GroovyShell shell;

    public GroovyShellMode(GroovyShellPlugin plugin) {
        super(plugin, "groovy");

        binding = new Binding();
        shell = new GroovyShell(binding);
    }

    public static class GroovyResult extends TaskResult{
        private final Exception e;
        private final Object object;

        public GroovyResult(Object object) {
            super(Result.OK);
            this.object = object;
            e = null;
        }

        public GroovyResult(Exception e) {
            super(Result.ERROR);
            this.e = e;
            this.object = null;
        }
    }

    public Task interpret(final String command, SessionContext $, Task parent, final TaskDef taskDef) {
        return new Task<TaskDef>(parent, taskDef, $) {
            @Override
            protected TaskResult exec(TaskRunner runner) {
                //local command
                boolean isLocal = !$(plugin.sendToHosts);

                Binding $binding;

                if (isLocal) {
                    $binding = binding;
                } else {
                    $binding = new Binding();
                    $binding.setVariable("$", $);
                    $binding.setVariable("parent", parent);
                    $binding.setVariable("taskDef", taskDef);
                    $binding.setVariable("runner", runner);
                    $binding.setVariable("executionContext", executionContext);
                    $binding.setVariable("task", this);
                    $binding.setVariable("_command", command);
                }

                GroovyShell $shell = isLocal ? shell : new GroovyShell($binding);

                try {
                    Object result = $shell.evaluate(command);

                    return new GroovyResult(result);
                } catch (Exception e) {
                    return new GroovyResult(e);
                }
            }
        };
    }

    @Override
    public Stage getStage() {
        return global.var(plugin.sendToHosts) ? super.getStage() : global.localStage;
    }

    @Override
    public void init() {
        GlobalContext global = plugin.getGlobal();
        //"$" if for conf, injected inside BearCommandLineConfigurator
        binding.setVariable("bear", plugin.bear);
        binding.setVariable("global", global);
        binding.setVariable("global", global);
        binding.setVariable("local", global.local);
        binding.setVariable("tasks", global.tasks);
        binding.setVariable("$$", global.localCtx);
//        binding.setVariable();
    }

    public Binding getLocalBinding() {
        return binding;
    }

    public void set$(BearCommandLineConfigurator configurator) {
        binding.setVariable("_", configurator);
    }


    public void completeCode(String script, int position){

    }



}
