package bear.main;

import bear.console.CompositeConsoleArrival;
import bear.core.*;
import bear.main.event.*;
import bear.plugins.Plugin;
import bear.plugins.groovy.GroovyShellPlugin;
import bear.session.DynamicVariable;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.exec.CommandExecutionEntry;
import bear.task.exec.TaskExecutionContext;
import chaschev.util.Exceptions;
import com.google.common.base.*;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import groovy.lang.GroovyShell;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static chaschev.lang.LangUtils.elvis;
import static com.google.common.collect.Lists.newArrayList;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBefore;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class BearScript {
    private static final Logger logger = LoggerFactory.getLogger(BearScript.class);

    final GlobalContext global;
    Bear bear;
    final BearFX bearFX;

    Plugin currentPlugin;

    private List<Class<? extends Plugin>> pluginList;

    private final List<String> currentScript = new ArrayList<String>();

    final IBearSettings settings;

    private GlobalContextFactory factory = GlobalContextFactory.INSTANCE;

    public BearScript(GlobalContext global, BearFX bearFX, Plugin currentPlugin, IBearSettings settings) {
        Preconditions.checkArgument(settings.isConfigured(), "settings not configured, call settings.configure(factory)");

        this.global = global;
        this.bearFX = bearFX;
        this.currentPlugin = currentPlugin;
        this.settings = settings;
        this.bear = global.bear;
    }

    private static class ShellRunContext{
        public final String sessionId = SessionContext.randomId();
        public final String taskId = SessionContext.randomId();

        /**
         * When <0 the method is synchronious. When =0, there is no timeout.
         */
        long timeout = -1;
        TimeUnit unit = null;
        String name;
        String script;


        public ShellRunContext setTimeout(long timeout) {
            this.timeout = timeout;
            return this;
        }

        public ShellRunContext setUnit(TimeUnit unit) {
            this.unit = unit;
            return this;
        }

        public ShellRunContext setName(String name) {
            this.name = name;
            return this;
        }

        public ShellRunContext setScript(String script) {
            this.script = script;
            return this;
        }
    }

    public Response exec(String script) {
        ShellRunContext shellRunContext = new ShellRunContext();

        Response lastResponse = null;

        bearFX.sendMessageToUI(new NewSessionConsoleEventToUI("shell", shellRunContext.sessionId));
        bearFX.sendMessageToUI(new TaskConsoleEventToUI("shell", shellRunContext.taskId + ", todo: add script name and date")
            .setId(shellRunContext.taskId)
            .setParentId(shellRunContext.sessionId));

        Splitter splitter = Splitter.on("\n")
            .trimResults()
            .omitEmptyStrings();

        for (String line : splitter.split(script)) {
            if (line.startsWith("#")) {
                continue;
            }

            if (line.startsWith(":")) {
                String firstWord = StringUtils.substringBetween(line, ":", " ");

                if ("use".equals(firstWord)) {
                    String command = substringAfter(line, " ").trim();
                    String secondWord = substringBefore(command, " ");

                    if ("shell".equals(secondWord)) {
                        Response runResult = runScript(shellRunContext);
                        lastResponse = elvis(runResult, lastResponse);

                        switchToPlugin(substringAfter(command, " ").trim(), shellRunContext);
                    } else {
                        bearFX.sendMessageToUI(new TextConsoleEventToUI("shell", "command not supported: <i>" + secondWord + "</i><br>")
                            .setParentId(shellRunContext.taskId));

                        return new MessageResponse("command not supported: " + secondWord);
                    }
                } else if ("set".equals(firstWord)) {
                    lastResponse = setVariable(line);
                }

                continue;
            }

            currentScript.add(line);
        }

        lastResponse = elvis(runScript(shellRunContext), lastResponse);

        return lastResponse;
    }

    private Response runScript(ShellRunContext shellContext) {
        if (currentScript.isEmpty()) {
            logger.info("skipping empty script before shell switch");
            return null;
        }

        Response last;

        if (currentPlugin.getShell().multiLine()) {
            shellContext.script = Joiner.on("\n").join(currentScript);
            shellContext.name = currentScript.get(0);
            bearFX.sendMessageToUI(new CommandConsoleEventToUI("shell", substringBefore(shellContext.script, "\n"))
                .setParentId(shellContext.taskId));

            last = runWithInterpreter(shellContext);
        } else {
            last = null;
            for (String s : currentScript) {
                bearFX.sendMessageToUI(new CommandConsoleEventToUI("shell", s)
                    .setParentId(shellContext.taskId));

                last = runWithInterpreter(shellContext
                    .setName(s).setScript(s)
//                    .setTimeout(-1).setUnit(null)
                );
            }
        }

        currentScript.clear();

        return last;
    }

    private RunResponse runWithInterpreter(final ShellRunContext shellContext) {
        try {
            logger.info("running with interpreter: '{}'", currentPlugin.toString());

            Stage stage = currentPlugin.getShell().getStage();

            if (stage != null) {
                global.putConst(bear.getStage, stage);
            }

            final RunResponse runResponse = runWithScript(new SingleTaskScript(new TaskDef(shellContext.name) {
                @Override
                public Task newSession(SessionContext $, Task parent) {
                    return currentPlugin.getShell().interpret(shellContext.script, $, parent, this);
                }
            }), shellContext);

            final CountDownLatch latch = new CountDownLatch(1);

            runResponse.runContext.arrivedCount.addListener(new DynamicVariable.ChangeListener<AtomicInteger>() {
                @Override
                public void changedValue(DynamicVariable<AtomicInteger> var, AtomicInteger oldValue, AtomicInteger newValue) {
                    if (newValue.get() == runResponse.runContext.size()) {
                        logger.info("finally home. removing stage from global scope");
                        global.removeConst(bear.getStage);

                        List<CompositeConsoleArrival.EqualityGroup> groups = runResponse.runContext.getConsoleArrival().divideIntoGroups();

                        bearFX.sendMessageToUI(
                            new AllTasksFinishedEventToUI(System.currentTimeMillis() - runResponse.runContext.getStartedAtMs(),
                                groups).setParentId(shellContext.taskId));

                        latch.countDown();
                    }

                }
            });


            if (shellContext.timeout >= 0) {
                if (shellContext.timeout > 0) {
                    latch.await(shellContext.timeout, shellContext.unit);
                }
            } else {
                //todo task timeout
                latch.await();
            }

            return runResponse;
        } catch (InterruptedException e) {
            throw Exceptions.runtime(e);
        }
    }

    private RunResponse runWithScript(Script script, ShellRunContext shellContext) {
        CompositeTaskRunContext context = new BearRunner(settings, script, factory)
//            .init()
            .prepareToRun();

        final CompositeConsoleArrival<SessionContext> consoleArrival = context.getConsoleArrival();

        List<SessionContext> $s = consoleArrival.getEntries();

        //todo this should not be async - this message might be slow
        bearFX.sendMessageToUI(new RMIEventToUI("terminals", "onScriptStart", getHosts($s)));
        bearFX.sendMessageToUI(new CommandConsoleEventToUI(
            "shell", shellContext.name
        ));


        for (final SessionContext $ : $s) {
            final SessionContext.ExecutionContext execContext = $.getExecutionContext();

            bearFX.sendMessageToUI(new NewSessionConsoleEventToUI($.getName(), $.id));

            execContext.textAppended.addListener(new DynamicVariable.ChangeListener<String>() {
                public void changedValue(DynamicVariable<String> var, String oldValue, String newValue) {
                    if (StringUtils.isNotEmpty(newValue)) {
                        bearFX.sendMessageToUI(
                            new TextConsoleEventToUI($.getName(), newValue)
                                .setParentId(execContext.currentCommand.getDefaultValue().command.id)
                        );
                    }
                }
            });

            execContext.currentCommand.addListener(new DynamicVariable.ChangeListener<CommandExecutionEntry>() {
                @Override
                public void changedValue(DynamicVariable<CommandExecutionEntry> var, CommandExecutionEntry oldValue, CommandExecutionEntry newValue) {
                    bearFX.sendMessageToUI(new CommandConsoleEventToUI($.getName(), newValue.toString())
                        .setId(newValue.command.id)
                        .setParentId(execContext.currentTask.getDefaultValue().id)
                    );
                }
            });

            execContext.currentTask.addListener(new DynamicVariable.ChangeListener<Task>() {
                @Override
                public void changedValue(DynamicVariable<Task> var, Task oldValue, Task newValue) {
                    bearFX.sendMessageToUI(
                        new TaskConsoleEventToUI($.getName(), newValue.toString())
                            .setId(newValue.id)
                            .setParentId($.id)
                    );
                }
            });

            execContext.rootExecutionContext.addListener(new DynamicVariable.ChangeListener<TaskExecutionContext>() {
                @Override
                public void changedValue(DynamicVariable<TaskExecutionContext> var, TaskExecutionContext oldValue, TaskExecutionContext newValue) {
                    if (newValue.taskResult != null) {
                        RootTaskFinishedEventToUI eventToUI = new RootTaskFinishedEventToUI(newValue.taskResult, newValue.getDuration(), $.getName());

                        bearFX.sendMessageToUI(eventToUI);
                    }
                }
            });
        }

        context.stats.addListener(new DynamicVariable.ChangeListener<CompositeTaskRunContext.Stats>() {
            @Override
            public void changedValue(DynamicVariable<CompositeTaskRunContext.Stats> var, CompositeTaskRunContext.Stats oldValue, CompositeTaskRunContext.Stats newValue) {
                bearFX.sendMessageToUI(new GlobalStatusEventToUI(newValue));
            }
        });

        context.submitTasks();

        return new RunResponse(context, getHosts(context.getConsoleArrival().getEntries()));
    }

    public static List<RunResponse.Host> getHosts(List<SessionContext> $s) {
        return Lists.transform($s, new Function<SessionContext, RunResponse.Host>() {
            public RunResponse.Host apply(SessionContext $) {
                return new RunResponse.Host($.sys.getName(), $.sys.getAddress());
            }
        });
    }

    private MessageResponse setVariable(String line) {
        String command = substringAfter(line, " ").trim();

        String varName = substringBefore(command, "=");
        String expression = substringAfter(command, "=");

        GroovyShell shell = new GroovyShell(global.getPlugin(GroovyShellPlugin.class).getShell().getLocalBinding());

        logger.info("evaluating: '{}'...", expression);

        Object o = shell.evaluate(expression);

        global.putConst(varName, o);

        return new MessageResponse(String.format("assigned '%s' to '%s'", varName, o));
    }

    private void switchToPlugin(final String pluginName, ShellRunContext shellRunContext) {
        List<Class<? extends Plugin>> matchingClasses = newArrayList(Collections2.filter(getPlugins(pluginName),
            new Predicate<Class<? extends Plugin>>() {
                @Override
                public boolean apply(Class<? extends Plugin> input) {
                    Shell shell = input.getAnnotation(Shell.class);
                    if (shell != null && shell.value().equalsIgnoreCase(pluginName)) return true;
                    return input.getSimpleName().toLowerCase().contains(pluginName);
                }
            }));

        if (matchingClasses.isEmpty()) {
            bearFX.sendMessageToUI(new TextConsoleEventToUI("shell", "no plugins found for '<i>" + pluginName + "</i>'\n")
                .setParentId(shellRunContext.taskId));
            throw new RuntimeException("no plugins found for '" + pluginName + "'");
        }

        if (matchingClasses.size() > 1) {
            bearFX.sendMessageToUI(new TextConsoleEventToUI("shell", "1+ plugins found for '<i>" + pluginName + "</i>': " + matchingClasses + "\n")
                .setParentId(shellRunContext.taskId));
            throw new RuntimeException("1+ plugins found for '" + pluginName + "': " + pluginName);
        }

        switchToPlugin(matchingClasses.get(0));
    }

    private SwitchResponse switchToPlugin(Class<? extends Plugin> aClass) {
//        bearFX.sendMessageToUI(new TextConsoleEventToUI("shell", "switching to plugin: <i>" + aClass.getSimpleName() +"</i>\n"));

        logger.info("switching to plugin: {}", aClass.getSimpleName());

        this.currentPlugin = global.getPlugin(aClass);

        SwitchResponse response = new SwitchResponse(currentPlugin.name, "$ " + currentPlugin.getShell().getCommandName());

        bearFX.sendMessageToUI(new TextConsoleEventToUI("shell", response.message + "\n"));

        return response;
    }

    private List<Class<? extends Plugin>> getPlugins(final String pluginName) {
        if (pluginList == null) {
            pluginList = new ArrayList<Class<? extends Plugin>>(new Reflections("bear.plugin")
                .getSubTypesOf(Plugin.class));
        }

        return pluginList;
    }

    public static class RunResponse extends Response {
        CompositeTaskRunContext runContext;

        public static class Host {
            public String name;
            public String address;

            public Host(String name, String address) {
                this.name = name;
                this.address = address;
            }
        }

        public List<Host> hosts;

        public RunResponse(CompositeTaskRunContext runContext, List<Host> hosts) {
            this.runContext = runContext;
            this.hosts = hosts;
        }
    }

    public Plugin getCurrentPlugin() {
        return currentPlugin;
    }

    public static class MessageResponse extends Response {
        public String message;

        public MessageResponse(String message) {
            this.message = message;
        }
    }

    public static class UIContext {
        public String settingsName;
        public String script;
    }

    public static class SwitchResponse extends MessageResponse {
        public final String pluginName;
        public final String prompt;

        public SwitchResponse(String pluginName, String prompt) {
            super("switched to shell '<i>" + pluginName + "</i>'");
            this.pluginName = pluginName;
            this.prompt = prompt;
        }
    }
}
