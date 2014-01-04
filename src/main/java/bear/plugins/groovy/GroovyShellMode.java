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

import bear.core.BearMain;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.core.Stage;
import bear.main.Script;
import bear.plugins.CommandInterpreter;
import bear.plugins.PluginShellMode;
import bear.session.Result;
import bear.task.SessionRunner;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.TaskResult;
import chaschev.lang.OpenBean;
import chaschev.util.CatchyCallable;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

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
    private static final org.apache.logging.log4j.Logger ui = LogManager.getLogger("fx");
    public static final Pattern SCRIPT_PATTERN = Pattern.compile(".*class.*extends.*Script.*", Pattern.MULTILINE | Pattern.DOTALL);
    public static final Pattern GRID_PATTERN = Pattern.compile("class.*extends.*Grid", Pattern.MULTILINE | Pattern.DOTALL);

    private final Binding binding;
    private final GroovyShell shell;

    public GroovyShellMode(GroovyShellPlugin plugin, String cmd) {
        super(plugin, cmd);

        binding = new Binding();
        shell = new GroovyShell(binding);
    }

    public static class GroovyResult extends TaskResult<GroovyResult> {
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

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("GroovyResult{");

            if(e != null){
                sb.append("e=").append(e.getMessage());
            }

            if(object != null){
                sb.append(", object=").append(object);
            }

            sb.append('}');

            return sb.toString();
        }
    }

    public Task interpret(final String command, SessionContext $, final Task _parent, final TaskDef taskDef) {
        Task<Object, TaskResult<?>> task = new Task<Object, TaskResult<?>>(_parent, taskDef, $) {
            @Override
            protected TaskResult<?> exec(final SessionRunner runner) {
                CatchyCallable<TaskResult<?>> callable = null;
                final Task<Object, TaskResult<?>> $this = this;
                try {
                    callable = new CatchyCallable<TaskResult<?>>(new Callable<TaskResult<?>>() {
                        public TaskResult<?> call() {
                            try {
                                if (SCRIPT_PATTERN.matcher(command).matches()) {
                                    GroovyClassLoader gcl = new GroovyClassLoader();
                                    Class clazz = gcl.parseClass(command);
                                    Object aScript = clazz.newInstance();
                                    Script script = (Script) aScript;
                                    script.setParent(_parent);
                                    $.wire(script);
                                    script.task = $this;
                                    script.configure();
                                    script.global = global;
                                    return script.run();
                                } else {
                                    GroovyShell shell = getShell(runner);
                                    shell.evaluate(command);
                                }
                            } catch (Throwable e) {
                                return TaskResult.of(e);
                            }

                            return TaskResult.OK;
                        }
                    });

                    return callable.call();
                } catch (IllegalStateException e) {
                    if (e.getMessage().contains("FX")) {
                        return fxWorkaround(callable);
                    } else {
                        logger.warn("", e);
                        return new GroovyResult(e);
                    }
                } catch (Exception e) {
                    logger.warn("", e);

                    return new GroovyResult(e);
                }
            }

            private TaskResult<?> fxWorkaround(CatchyCallable<TaskResult<?>> callable) {
                TaskResult<?> result;

                try {
                    Future<TaskResult<?>> fut = (Future<TaskResult<?>>) OpenBean.invoke(binding.getVariable("_"), "evaluateInFX", callable);
                    result = fut.get();
                } catch (Exception e1) {
                    logger.warn("", e1);
                    result = new GroovyResult(e1);
                }

                return result;
            }

            private GroovyShell getShell(SessionRunner runner) {
                boolean isLocal = !$(plugin.sendToHosts);

                Binding $binding;

                if (isLocal) {
                    $binding = binding;
                } else {
                    $binding = new Binding();
                    $binding.setVariable("_", $);
                    $binding.setVariable("sys", $.sys);
                    $binding.setVariable("parent", getParent());
                    $binding.setVariable("bear", bear);
                    $binding.setVariable("global", global);
                    $binding.setVariable("tasks", global.tasks);
                    $binding.setVariable("taskDef", taskDef);
                    $binding.setVariable("runner", runner);
                    $binding.setVariable("executionContext", getExecutionContext());
                    $binding.setVariable("task", this);
                    $binding.setVariable("_command", command);
                }

                $binding.setVariable("logger", logger);
                $binding.setVariable("ui", ui);

                return isLocal ? shell : new GroovyShell($binding);
            }
        };

        return task;
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

    public void set$(BearMain configurator) {
        binding.setVariable("_", configurator);
    }


    public Replacements completeCode(String script, int position){
        return new GroovyCodeCompleter(binding, shell).completeCode(script, position);
    }
}
