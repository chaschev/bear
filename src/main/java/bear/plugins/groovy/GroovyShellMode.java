/*
 * Copyright (C) 2013 Andrey Chaschev.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bear.plugins.groovy;

import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.core.Stage;
import bear.main.Cli;
import bear.plugins.CommandInterpreter;
import bear.plugins.PluginShellMode;
import bear.session.Result;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.TaskResult;
import bear.task.TaskRunner;
import chaschev.lang.OpenBean;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(GroovyShellMode.class);

    private final Binding binding;
    private final GroovyShell shell;

    public GroovyShellMode(GroovyShellPlugin plugin, String cmd) {
        super(plugin, cmd);

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
                try {
                    GroovyShell shell = getShell(runner);
                    Object result = shell.evaluate(command);

                    return new GroovyResult(result);
                }
                catch (IllegalStateException e){
                    if(e.getMessage().contains("FX")){
                        return fxWorkaround();
                    }else{
                        logger.warn("", e);
                        return new GroovyResult(e);
                    }
                }
                catch (Exception e) {
                    logger.warn("", e);

                    return new GroovyResult(e);
                }
            }

            private GroovyResult fxWorkaround() {
                GroovyResult result;

                try {
                    OpenBean.invoke(binding.getVariable("_"), "evaluateInFX", new Runnable() {
                        @Override
                        public void run() {
                            shell.evaluate(command);
                        }
                    });
                    result = new GroovyResult("sent to FX evaluation");
                } catch (Exception e1) {
                    logger.warn("", e1);
                    result = new GroovyResult(e1);
                }
                return result;
            }

            private GroovyShell getShell(TaskRunner runner) {
                boolean isLocal = !$(plugin.sendToHosts);

                Binding $binding;

                if (isLocal) {
                    $binding = binding;
                } else {
                    $binding = new Binding();
                    $binding.setVariable("_", $);
                    $binding.setVariable("parent", parent);
                    $binding.setVariable("bear", bear);
                    $binding.setVariable("global", global);
                    $binding.setVariable("tasks", global.tasks);
                    $binding.setVariable("taskDef", taskDef);
                    $binding.setVariable("runner", runner);
                    $binding.setVariable("executionContext", executionContext);
                    $binding.setVariable("task", this);
                    $binding.setVariable("_command", command);
                }

                return isLocal ? shell : new GroovyShell($binding);
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
        binding.setVariable("global", global);
        binding.setVariable("bear", global.bear);
        binding.setVariable("local", global.local);
        binding.setVariable("tasks", global.tasks);
        binding.setVariable("$$", global.localCtx);
//        binding.setVariable();
    }

    public Binding getLocalBinding() {
        return binding;
    }

    public void set$(Cli configurator) {
        binding.setVariable("_", configurator);
    }


    public Replacements completeCode(String script, int position){
        return new GroovyCodeCompleter(binding, shell).completeCode(script, position);
    }
}
