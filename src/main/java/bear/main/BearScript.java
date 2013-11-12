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

package bear.main;

import bear.console.CompositeConsoleArrival;
import bear.console.GroupDivider;
import bear.core.*;
import bear.main.event.*;
import bear.plugins.Plugin;
import bear.plugins.groovy.GroovyShellPlugin;
import bear.session.DynamicVariable;
import bear.session.Result;
import bear.task.*;
import bear.task.exec.CommandExecutionEntry;
import bear.task.exec.TaskExecutionContext;
import chaschev.util.CatchyCallable;
import com.google.common.base.*;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import groovy.lang.GroovyShell;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.collect.Lists.newArrayList;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBefore;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

/**
 * Each script runs in a separate thread, because one thread execution must not suspend/fail all others.
 *
 * This is why vars are set in sessions. So todo is to implement set global var value. Note - it has completely different semantics, setting it inside a session could lead to an error
 */
public class BearScript {
    private static final Logger logger = LoggerFactory.getLogger(BearScript.class);
    private static final org.apache.logging.log4j.Logger ui = LogManager.getLogger("fx");

//    final DynamicVariable<BearScriptPhase> phaseId = new DynamicVariable<BearScriptPhase>();

    final GlobalContext global;
    final Plugin initialPlugin;
    Bear bear;
    final BearFX bearFX;

    private List<Class<? extends Plugin>> pluginList;

    final IBearSettings settings;

    private GlobalContextFactory factory = GlobalContextFactory.INSTANCE;

    public BearScript(GlobalContext global, BearFX bearFX, Plugin currentPlugin, IBearSettings settings) {
        Preconditions.checkArgument(settings.isConfigured(), "settings not configured, call settings.configure(factory)");

        this.global = global;
        this.bearFX = bearFX;
        this.initialPlugin = currentPlugin;
        this.settings = settings;
        this.bear = global.bear;
    }

    static class ScriptItem {
        final String id = SessionContext.randomId();
        int startsAtIndex;
        String pluginName;
        List<String> lines;

        ScriptItem(String pluginName, List<String> lines, int startsAtIndex) {
            this.pluginName = pluginName;
            this.lines = lines;
            this.startsAtIndex = startsAtIndex;
        }

        public String asOneLineDesc(){
            return pluginName + ": " + lines.get(0);
        }
    }


    public static final class ScriptError{
        String line;
        int index;
        String comment;

        public ScriptError(String line, int index, String comment) {
            this.line = line;
            this.index = index;
            this.comment = comment;
        }
    }

    public class BearScriptExecContext{
        String pluginName;

        SessionContext $;
        private final Task parent;
        private final TaskDef taskDef;

        List<ScriptError> errors = new ArrayList<ScriptError>();
        private Plugin currentPlugin;

        public BearScriptExecContext(Plugin currentPlugin, SessionContext $, Task parent, TaskDef taskDef) {
            this.currentPlugin = currentPlugin;
            pluginName = currentPlugin.cmdAnnotation();
            this.$ = $;
            this.parent = parent;
            this.taskDef = taskDef;
        }

        private TaskResult runItem(ShellRunContext shellContext, ScriptItem scriptItem){
            Response lastResponse = null;

            List<String> filteredLines = new ArrayList<String>(scriptItem.lines.size());

            List<String> lines = scriptItem.lines;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.startsWith(":")) {
                    String firstWord = StringUtils.substringBetween(line, ":", " ");

                    if ("set".equals(firstWord)) {
                        lastResponse = setVariable(line);
                    } else {
                        errors.add(new ScriptError(
                            line,
                            scriptItem.startsAtIndex + i, "unknown command: " + firstWord));
                    }
                }else{
                    filteredLines.add(line);
                }
            }

            switchToPlugin(scriptItem.pluginName, shellContext);
            if(!filteredLines.isEmpty()){
                return runWithInterpreter(filteredLines, shellContext, scriptItem);
            }else {
                return TaskResult.OK;
            }
        }


        private TaskResult runWithInterpreter(List<String> lines, final ShellRunContext shellContext, ScriptItem scriptItem) {
            TaskResult last = TaskResult.OK;

            if (currentPlugin.getShell().multiLine()) {
                shellContext.script = Joiner.on("\n").join(lines);
                shellContext.name = lines.get(0);
                bearFX.sendMessageToUI(newShellCommand(substringBefore(shellContext.script, "\n"), shellContext));
//              bearFX.sendMessageToUI(new CommandConsoleEventToUI("shell", substringBefore(shellContext.script, "\n"))
//                .setParentId(shellContext.taskId));

                Task<?> task = currentPlugin.getShell().interpret(shellContext.script, $, parent, taskDef);


//                bearFX.sendMessageToUI(
//                    new TaskConsoleEventToUI("shell", scriptItem.asOneLineDesc())
//                        .setId(shellContext.taskId)
//                       .setParentId(shellContext.sessionId)
//                );

                long startedAt = System.currentTimeMillis();

                BearScriptPhase phase = null;

                try {
                    phase = startNewPhase(shellContext, scriptItem, scriptItem.id);

                    last = $.runner.runSession(task);
                } catch (Exception e) {
                    last = new DependencyResult(Result.ERROR).add(e.toString());
                }finally {
                    long duration = System.currentTimeMillis() - startedAt;
                    assert phase != null;
                    phase.addArrival($, duration, last);
                }
            } else {
                last = null;
                for (int i = 0; i < lines.size(); i++) {
                    String s = lines.get(i);

                    shellContext.script = s;

                    if(i == 0){
                        bearFX.sendMessageToUI(newShellCommand(substringBefore(s, "\n") + "...", shellContext));
                    }

                    bearFX.sendMessageToUI(new CommandConsoleEventToUI("shell", s)
                        .setId(SessionContext.randomId())
                        .setParentId(shellContext.phaseId));

                    Task<?> task = currentPlugin.getShell().interpret(shellContext.script, $, parent, taskDef);

                    long startedAt = System.currentTimeMillis();

                    BearScriptPhase phase = null;

                    try {
                        phase = startNewPhase(shellContext, scriptItem, scriptItem.id + i);
                        last = $.runner.runSession(task);
                    }finally {
                        long duration = System.currentTimeMillis() - startedAt;
                        phase.addArrival($, duration, last);
                    }

                    if(!last.ok()){
                        errors.add(new ScriptError(s, i + 1, "error during line exec:" + last));
                        return last;
                    }
                }
            }

            return last;
        }

        private BearScriptPhase startNewPhase(ShellRunContext shellContext, ScriptItem scriptItem, String phaseId) {
            shellContext.phaseId = phaseId;
            BearScriptPhase phase = shellContext.phases.getPhase(shellContext.phaseId, scriptItem);

            StringBuilder phaseSB = $.getExecutionContext().phaseText.getDefaultValue();
            phaseSB.setLength(0);
            $.getExecutionContext().phaseText.fireExternalModification();
            $.getExecutionContext().phaseName = scriptItem.asOneLineDesc();
            $.getExecutionContext().phaseId.defaultTo(shellContext.phaseId);
            return phase;
        }

        public EventToUI newShellCommand(String title, ShellRunContext shellContext) {
            return new CommandConsoleEventToUI($.getName(), title)
                .setId($.getExecutionContext().currentTask.getDefaultValue().id)
                .setParentId(shellContext.sessionId);
        }
        private MessageResponse setVariable(String line) {
            String command = substringAfter(line, " ").trim();

            String varName = substringBefore(command, "=");
            String expression = substringAfter(command, "=");

            GroovyShell shell = new GroovyShell(global.getPlugin(GroovyShellPlugin.class).getShell().getLocalBinding());

            logger.info("evaluating: '{}'...", expression);

            Object o = shell.evaluate(expression);

            $.putConst(varName, o);

            return new MessageResponse(String.format("assigned '%s' to '%s'", varName, o));
        }

        private void switchToPlugin(final String pluginName, ShellRunContext shellRunContext) {
            Preconditions.checkNotNull(pluginName, "plugin name is null");

            List<Class<? extends Plugin>> matchingClasses = newArrayList(Collections2.filter(getPlugins(),
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
                    .setParentId(shellRunContext.phaseId));
                throw new RuntimeException("no plugins found for '" + pluginName + "'");
            }

            if (matchingClasses.size() > 1) {
                bearFX.sendMessageToUI(new TextConsoleEventToUI("shell", "1+ plugins found for '<i>" + pluginName + "</i>': " + matchingClasses + "\n")
                    .setParentId(shellRunContext.phaseId));
                throw new RuntimeException("1+ plugins found for '" + pluginName + "': " + pluginName);
            }

            switchToPlugin(matchingClasses.get(0), shellRunContext);
        }

        private SwitchResponse switchToPlugin(Class<? extends Plugin> aClass, ShellRunContext shellContext) {
//        bearFX.sendMessageToUI(new TextConsoleEventToUI("shell", "switching to plugin: <i>" + aClass.getSimpleName() +"</i>\n"));

            logger.info("switching to plugin: {}", aClass.getSimpleName());

            this.currentPlugin = global.getPlugin(aClass);

            SwitchResponse response = new SwitchResponse(currentPlugin.name, "$ " + currentPlugin.getShell().getCommandName());

//            bearFX.sendMessageToUI(new TextConsoleEventToUI("shell", response.message + "\n")
//                .setParentId(shellContext.phaseId));

            return response;
        }
    }

    public static class BearScriptPhase{
        final String id;
        final AtomicInteger partiesArrived = new AtomicInteger();
        public final AtomicInteger partiesOk = new AtomicInteger();

        final AtomicLong minimalOkDuration = new AtomicLong(-1);

        BearFX bearFX;
        private final ShellRunContext shellContext;

        GroupDivider<SessionContext> groupDivider;

        public final long startedAtMs = System.currentTimeMillis();

        final int partiesCount;

        public int partiesPending;
        public int partiesFailed = 0;

        public BearScriptPhase(String id, BearFX bearFX, GroupDivider<SessionContext> groupDivider, ShellRunContext shellContext) {
            this.id = id;
            this.bearFX = bearFX;
            this.shellContext = shellContext;
            this.partiesCount = groupDivider.getEntries().size();
            this.groupDivider = groupDivider;
        }

        public void addArrival(SessionContext $, final long duration, TaskResult result) {
            groupDivider.addArrival($);

            if (result.ok()) {
                partiesOk.incrementAndGet();

                if(minimalOkDuration.compareAndSet(-1, duration)){
                    $.getGlobal().scheduler.schedule(new CatchyCallable<Void>(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            boolean haveHangUpJobs;
                            boolean alreadyFinished;

                            if (partiesArrived.compareAndSet(partiesCount, -1)) {
                                alreadyFinished = false;
                                haveHangUpJobs = false;
                            }else{
                                if (partiesArrived.compareAndSet(-1, -1)) {
                                    haveHangUpJobs = false;
                                    alreadyFinished = true;
                                }else{
                                    alreadyFinished = false;
                                    haveHangUpJobs = true;
                                }
                            }

                            if(!alreadyFinished){
                                sendPhaseResults(duration);
                            }
                            return null;
                        }
                    }), duration * 3, TimeUnit.MILLISECONDS);
                }
            }


            partiesArrived.incrementAndGet();

            partiesPending = partiesCount - partiesArrived.get();
            partiesFailed = partiesArrived.get() - partiesOk.get();

            if(partiesArrived.compareAndSet(partiesCount, -1)){
                sendPhaseResults(duration);
            }
        }

        private void sendPhaseResults(long duration) {
            List<CompositeConsoleArrival.EqualityGroup> groups = groupDivider.divideIntoGroups();

            bearFX.sendMessageToUI(
                new AllTasksFinishedEventToUI(duration, groups)
                    .setParentId(shellContext.phaseId));
        }
    }

    public static class BearScriptPhases{
        final ConcurrentHashMap<String, BearScriptPhase> phases = new ConcurrentHashMap<String, BearScriptPhase>();
        private final List<SessionContext> $s;
        BearFX bearFX;
        private final ShellRunContext shellContext;

        public BearScriptPhases(List<SessionContext> $s, BearFX bearFX, ShellRunContext shellContext) {
            this.$s = $s;
            this.bearFX = bearFX;
            this.shellContext = shellContext;
        }

        final Object phaseCreationLock = new Object();

        public BearScriptPhase getPhase(String id, ScriptItem scriptItem) {
            BearScriptPhase phase = phases.get(id);

            if(phase != null){
                return phase;
            }

            synchronized (phaseCreationLock){
                phase = phases.get(id);
                if(phase == null){
                    GroupDivider<SessionContext> divider = new GroupDivider<SessionContext>($s, Stage.SESSION_ID, new Function<SessionContext, String>() {
                        public String apply(SessionContext $) {
                            DynamicVariable<Task> task = $.getExecutionContext().currentTask;
                            return task.isUndefined() ? null : task.getDefaultValue().id;
                        }
                    }, new Function<SessionContext, String>() {
                        @Override
                        public String apply(SessionContext $) {
                            return $.getExecutionContext().phaseText.getDefaultValue().toString();
                        }
                    });

                    phases.put(id, phase = new BearScriptPhase(id, bearFX, divider, shellContext));

                    bearFX.sendMessageToUI(new TaskConsoleEventToUI("shell", "step " + scriptItem.asOneLineDesc(), null)
                        .setId(shellContext.phaseId)
                        .setParentId(shellContext.sessionId)
                    );
                }
            }

            Preconditions.checkNotNull(phase, "result is null!");

            return phase;
        }
    }

    static class ShellRunContext{
        public final String sessionId = SessionContext.randomId();
        // seen as a task
        protected String phaseId;

        /**
         * When <0 the method is synchronous. When =0, there is no timeout.
         */
        long timeout = -1;
        TimeUnit unit = null;
        String name;
        String script;

        ShellRunContext(BearScriptPhases phases) {
            this.phases = phases;
        }

        public BearScriptPhases phases;

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

        public String getPhaseId() {
            return phaseId;
        }

        public void newTaskId(){
            phaseId = SessionContext.randomId();
        }
    }

    public Response exec(String script, boolean interactive) {
        if(interactive){
            global.putConst(bear.internalInteractiveRun, true);
        }

        final ShellRunContext shellContext = new ShellRunContext(null);

        bearFX.sendMessageToUI(new NewSessionConsoleEventToUI("shell", shellContext.sessionId));

        final BearScriptParseResult parseResult = parseScript(script, shellContext, initialPlugin.cmdAnnotation());

        if(!parseResult.globalErrors.isEmpty()){
            return new RunResponse(parseResult.globalErrors);
        }

        Supplier<SingleTaskScript> singleTaskScriptSupplier = Suppliers.ofInstance(new SingleTaskScript(new TaskDef() {
            @Override
            public Task newSession(SessionContext $, Task parent) {
                final TaskDef taskDef = this;

                return new Task<TaskDef>(parent, taskDef, $) {
                    @Override
                    protected TaskResult exec(TaskRunner runner) {
                        BearScriptExecContext scriptExecContext =
                            new BearScriptExecContext(initialPlugin, $, parent, taskDef);

                        TaskResult result = TaskResult.OK;

                        for (ScriptItem scriptItem : parseResult.scriptItems) {
                            result = scriptExecContext.runItem(shellContext, scriptItem);

                            if (result.nok()) {
                                break;
                            }
                        }

                        return result;
                    }
                };
            }
        }));

        final CompositeTaskRunContext runContext = new BearRunner(settings, singleTaskScriptSupplier, factory)
            .prepareToRun();

        global.currentGlobalRunContext = runContext;

        final CompositeConsoleArrival<SessionContext> consoleArrival = runContext.getConsoleArrival();

        List<SessionContext> $s = consoleArrival.getEntries();

        shellContext.phases = new BearScriptPhases($s, bearFX, shellContext);

        //todo this should not be async - this message might be slow
        bearFX.sendMessageToUI(new RMIEventToUI("terminals", "onScriptStart", getHosts($s)));

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
                    if($.getExecutionContext().phaseId.isUndefined()){
                       return;
                    }

                    String phaseId = $.getExecutionContext().phaseId.getDefaultValue();

                    bearFX.sendMessageToUI(
                        new TaskConsoleEventToUI($.getName(), $.getExecutionContext().phaseName + " " + phaseId, phaseId)
                            .setId(newValue.id)
                            .setParentId($.id)
                    );
                }
            });

            execContext.rootExecutionContext.addListener(new DynamicVariable.ChangeListener<TaskExecutionContext>() {
                @Override
                public void changedValue(DynamicVariable<TaskExecutionContext> var, TaskExecutionContext oldValue, TaskExecutionContext newValue) {
                    if (newValue.isFinished()) {
                        RootTaskFinishedEventToUI eventToUI = new RootTaskFinishedEventToUI(newValue.taskResult, newValue.getDuration(), $.getName());

                        bearFX.sendMessageToUI(eventToUI);
                    }
                }
            });
        }

        runContext.stats.addListener(new DynamicVariable.ChangeListener<CompositeTaskRunContext.Stats>() {
            @Override
            public void changedValue(DynamicVariable<CompositeTaskRunContext.Stats> var, CompositeTaskRunContext.Stats oldValue, CompositeTaskRunContext.Stats newValue) {
                bearFX.sendMessageToUI(new GlobalStatusEventToUI(newValue));
            }
        });

        runContext.submitTasks();

        runContext.arrivedCount.addListener(new DynamicVariable.ChangeListener<AtomicInteger>() {
            @Override
            public void changedValue(DynamicVariable<AtomicInteger> var, AtomicInteger oldValue, AtomicInteger newValue) {
                if (newValue.get() == runContext.size()) {
                    logger.info("finally home. removing stage from global scope");

                    global.currentGlobalRunContext = null;

//                        global.removeConst(bear.getStage);

                    global.removeConst(bear.internalInteractiveRun);

                    List<CompositeConsoleArrival.EqualityGroup> groups = runContext.getConsoleArrival().divideIntoGroups();

                    bearFX.sendMessageToUI(
                        new AllTasksFinishedEventToUI(System.currentTimeMillis() - runContext.getStartedAtMs(),
                            groups).setParentId(shellContext.phaseId));
                }
            }
        });

        return new RunResponse(runContext, getHosts(runContext.getConsoleArrival().getEntries()));
    }


    public static class BearScriptParseResult{
        List<ScriptItem> scriptItems;
        List<ScriptError> globalErrors;

        public BearScriptParseResult(List<ScriptItem> scriptItems, List<ScriptError> globalErrors) {
            this.scriptItems = scriptItems;
            this.globalErrors = globalErrors;
        }
    }

    static BearScriptParseResult parseScript(String script, ShellRunContext shellContext, String initialPluginName) {
        final List<String> currentScript = new ArrayList<String>();

        List<ScriptItem> scriptItems = new ArrayList<ScriptItem>();
        List<ScriptError> globalErrors = new ArrayList<ScriptError>();

        Splitter splitter = Splitter.on("\n").trimResults();

        String currentPluginName = initialPluginName;

        int lineIndex = 1;

        for (Iterator<String> iterator = splitter.split(script).iterator(); iterator.hasNext(); lineIndex++) {
            String line = iterator.next();

            if (line.startsWith("#")) {
                continue;
            }

            if (line.startsWith(":")) {
                String firstWord = StringUtils.substringBetween(line, ":", " ");

                if ("use".equals(firstWord)) {
                    String command = substringAfter(line, " ").trim();
                    String secondWord = substringBefore(command, " ");

                    if ("shell".equals(secondWord)) {
                        if(!currentScript.isEmpty()){
                            scriptItems.add(new ScriptItem(currentPluginName, new ArrayList<String>(currentScript), lineIndex));

                            currentScript.clear();
                        }

                        currentPluginName = substringAfter(command, " ").trim();
                    } else {
                        ui.error(new TextConsoleEventToUI("shell", "command not supported: <i>" + secondWord + "</i><br>")
                            .setParentId(shellContext.phaseId));

                        globalErrors.add(new ScriptError(line, lineIndex, "command not supported: " + secondWord));
                    }

                    continue;
                } else{
                    currentScript.add(line);
                }

                continue;
            }

            currentScript.add(line);
        }

        if(!currentScript.isEmpty()){
            scriptItems.add(new ScriptItem(currentPluginName, new ArrayList<String>(currentScript), lineIndex));
        }

        return new BearScriptParseResult(scriptItems, globalErrors);
    }




    public static List<RunResponse.Host> getHosts(List<SessionContext> $s) {
        return Lists.transform($s, new Function<SessionContext, RunResponse.Host>() {
            public RunResponse.Host apply(SessionContext $) {
                return new RunResponse.Host($.sys.getName(), $.sys.getAddress());
            }
        });
    }





    private List<Class<? extends Plugin>> getPlugins() {
        if (pluginList == null) {
            pluginList = new ArrayList<Class<? extends Plugin>>(new Reflections("bear.plugin")
                .getSubTypesOf(Plugin.class));
        }

        return pluginList;
    }

    public static class RunResponse extends Response {
        public List<ScriptError> errors;
        CompositeTaskRunContext runContext;

        public RunResponse(List<ScriptError> errors) {
            this.errors = errors;
        }

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
        return null;
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
