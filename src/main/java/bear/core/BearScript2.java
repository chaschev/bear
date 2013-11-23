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

package bear.core;

import bear.console.ConsolesDivider;
import bear.console.GroupDivider;
import bear.main.BearFX;
import bear.main.BearRunner2;
import bear.main.Response;
import bear.main.event.RMIEventToUI;
import bear.main.event.TextConsoleEventToUI;
import bear.plugins.Plugin;
import bear.plugins.groovy.GroovyShellPlugin;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.TaskResult;
import chaschev.util.CatchyCallable;
import chaschev.util.Exceptions;
import com.google.common.base.*;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import groovy.lang.GroovyShell;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static chaschev.lang.Predicates2.contains;
import static com.google.common.base.Optional.of;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.transform;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBefore;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

/**
 * Each script runs in a separate thread, because one thread execution must not suspend/fail all others.
 * <p/>
 * This is why vars are set in sessions. So todo is to implement set global var value. Note - it has completely different semantics, setting it inside a session could lead to an error
 * todo move shellContext and other to run context, separate BearScript parsing from executing
 */
public class BearScript2 {
    private static final Logger logger = LoggerFactory.getLogger(BearScript2.class);
    private static final org.apache.logging.log4j.Logger ui = LogManager.getLogger("fx");

    public static final Splitter LINE_SPLITTER = Splitter.on("\n").trimResults();

//    final DynamicVariable<BearScriptPhase> phaseId = new DynamicVariable<BearScriptPhase>();

    final GlobalContext global;
    final Plugin initialPlugin;
    Bear bear;
    final BearFX bearFX;

    private List<Class<? extends Plugin>> pluginList;

    final IBearSettings settings;

    private GlobalContextFactory factory = GlobalContextFactory.INSTANCE;

    public BearScript2(GlobalContext global, BearFX bearFX, Plugin currentPlugin, IBearSettings settings) {
        checkArgument(settings.isConfigured(), "settings not configured, call settings.configure(factory)");

        this.global = global;
        this.bearFX = bearFX;
        this.initialPlugin = currentPlugin;
        this.settings = settings;
        this.bear = global.bear;
    }

    static class ScriptItem {
        Optional<String> scriptName;
        int startsAtIndex;
        String pluginName;
        final List<String> lines;

        ScriptItem(Optional<String> scriptName, String pluginName, int startsAtIndex) {
            this.scriptName = scriptName;
            this.pluginName = pluginName;
            this.startsAtIndex = startsAtIndex;
            lines = new ArrayList<String>();
        }

        ScriptItem(Optional<String> scriptName, String pluginName, List<String> lines, int startsAtIndex) {
            if (scriptName.isPresent()) {
                String s = scriptName.get();
                if(s.indexOf('/') !=-1 || s.indexOf('\\') != -1){
                    scriptName = of(FilenameUtils.getName(s));
                }
            }

            this.scriptName = scriptName;
            this.pluginName = pluginName;
            this.lines = lines;
            this.startsAtIndex = startsAtIndex;
        }

        String getScriptName() {
            return scriptName.or(asOneLineDesc());
        }

        public String asOneLineDesc() {
            Optional<String> nonCommandLine = Iterables.tryFind(lines, not(contains(":set")));

            String line = nonCommandLine.or(lines.get(0));

            return pluginName + ": " + line;
        }

        public boolean isEmpty() {
            return lines.isEmpty();
        }
    }


    public static final class ScriptError {
        String line;
        int index;
        String comment;

        public ScriptError(String line, int index, String comment) {
            this.line = line;
            this.index = index;
            this.comment = comment;
        }
    }

    /**
     * Scope: SESSION.
     */
    public class BearScriptExecContext {
        String pluginName;

        List<ScriptError> errors = new ArrayList<ScriptError>();

        public BearScriptExecContext(Plugin currentPlugin) {
            pluginName = currentPlugin.cmdAnnotation();
        }

        /**
         * Scope: SESSION
         *
         * @param shellContext
         * @param scriptItem
         * @return
         */
        private TaskDef<Task> convertItemToTask(final ShellSessionContext shellContext, final ScriptItem scriptItem) {

            final List<String> executableLines = new ArrayList<String>(scriptItem.lines.size());
            final List<String> directivesLines = new ArrayList<String>();

            final List<String> lines = scriptItem.lines;

            for (String line : lines) {
                if (line.startsWith(":")) {
                    directivesLines.add(line);
                } else {
                    executableLines.add(line);
                }
            }



            if (!executableLines.isEmpty()) {
                return new TaskDef<Task>(scriptItem.getScriptName()){
                    @Override
                    public Task newSession(SessionContext $, Task parent) {
                        final Plugin currentPlugin = getPlugin(scriptItem.pluginName, shellContext);

                        for (int i = 0; i < directivesLines.size(); i++) {
                            String line = directivesLines.get(i);
                            String firstWord = StringUtils.substringBetween(line, ":", " ");

                            if ("set".equals(firstWord)) {
                                setVariable(line, $);
                            } else {
                                errors.add(new ScriptError(
                                    line,
                                    scriptItem.startsAtIndex + i, "unknown command: " + firstWord));
                            }
                        }

                        if (currentPlugin.getShell().multiLine()) {
//                            shellContext.name = executableLines.get(0);
                            String script = Joiner.on("\n").join(executableLines);

                            return currentPlugin.getShell().interpret(script, $, parent, null);
                        }else{
                            throw new UnsupportedOperationException("todo copy from an old version");
                        }
                    }
                };
            } else {
                return TaskDef.EMPTY;
            }
        }


        private MessageResponse setVariable(String line, SessionContext $) {
            String command = substringAfter(line, " ").trim();

            String varName = substringBefore(command, "=");
            String expression = substringAfter(command, "=");

            GroovyShell shell = new GroovyShell(global.getPlugin(GroovyShellPlugin.class).getShell().getLocalBinding());

            logger.info("evaluating: '{}'...", expression);

            Object o = shell.evaluate(expression);

            $.putConst(varName, o);

            return new MessageResponse(String.format("assigned '%s' to '%s'", varName, o));
        }

        private Plugin getPlugin(final String pluginName, ShellSessionContext shellSessionContext) {
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
                    .setParentId(shellSessionContext.phaseId));
                throw new RuntimeException("no plugins found for '" + pluginName + "'");
            }

            if (matchingClasses.size() > 1) {
                bearFX.sendMessageToUI(new TextConsoleEventToUI("shell", "1+ plugins found for '<i>" + pluginName + "</i>': " + matchingClasses + "\n")
                    .setParentId(shellSessionContext.phaseId));
                throw new RuntimeException("1+ plugins found for '" + pluginName + "': " + pluginName);
            }

            return global.getPlugin(matchingClasses.get(0));

//            return new SwitchResponse(currentPlugin.name, "$ " + currentPlugin.getShell().getCommandName());
        }

    }

    public static class BearScriptPhase {
        final String id;
        final AtomicInteger partiesArrived = new AtomicInteger();
        public final AtomicInteger partiesOk = new AtomicInteger();

        final AtomicLong minimalOkDuration = new AtomicLong(-1);

        BearFX bearFX;
        private final ShellSessionContext shellContext;
        private final ScriptItem scriptItem;

        GroupDivider<SessionContext> groupDivider;

//        public final long startedAtMs = System.currentTimeMillis();

        final int partiesCount;

        public int partiesPending;
        public int partiesFailed = 0;

        public BearScriptPhase(String id, BearFX bearFX, GroupDivider<SessionContext> groupDivider, ShellSessionContext shellContext, ScriptItem scriptItem) {
            this.id = id;
            this.bearFX = bearFX;
            this.shellContext = shellContext;
            this.scriptItem = scriptItem;
            this.partiesCount = groupDivider.getEntries().size();
            this.groupDivider = groupDivider;
        }

        public void addArrival(SessionContext $, final long duration, TaskResult result) {
            groupDivider.addArrival($);

            if (result.ok()) {
                partiesOk.incrementAndGet();

                if (minimalOkDuration.compareAndSet(-1, duration)) {
                    $.getGlobal().scheduler.schedule(new CatchyCallable<Void>(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            boolean haveHangUpJobs;
                            boolean alreadyFinished;

                            if (partiesArrived.compareAndSet(partiesCount, -1)) {
                                alreadyFinished = false;
                                haveHangUpJobs = false;
                            } else {
                                if (partiesArrived.compareAndSet(-1, -1)) {
                                    haveHangUpJobs = false;
                                    alreadyFinished = true;
                                } else {
                                    alreadyFinished = false;
                                    haveHangUpJobs = true;
                                }
                            }

                            if (!alreadyFinished) {
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

            if (partiesArrived.compareAndSet(partiesCount, -1)) {
                sendPhaseResults(duration);
            }
        }

        private void sendPhaseResults(long duration) {
            List<ConsolesDivider.EqualityGroup> groups = groupDivider.divideIntoGroups();


        }
    }

    static class ShellSessionContext {
        public final String sessionId = SessionContext.randomId();
        // seen as a task
        protected String phaseId;

        ShellSessionContext() {

        }


        public String getPhaseId() {
            return phaseId;
        }

        public void newTaskId() {
            phaseId = SessionContext.randomId();
        }
    }

    /**
     * @param scriptSupplier A supplier for a script, i.e. a parser or a single item (at the moment a groovy script).
     */
    public Response exec(boolean interactive, Supplier<BearScriptParseResult> scriptSupplier) {
        final ShellSessionContext shellContext = new ShellSessionContext();

        final BearScriptParseResult parseResult = scriptSupplier.get();

        if (!parseResult.globalErrors.isEmpty()) {
            return new RunResponse(parseResult.globalErrors);
        }

        final List<ScriptItem> scriptItems = parseResult.scriptItems;

        if (interactive) {
            global.putConst(bear.internalInteractiveRun, true);
        }

        // this should not be ran as a single task
        // OR this could be ran as a single task

        final BearScriptExecContext scriptExecContext =
            new BearScriptExecContext(initialPlugin);

        List<TaskDef<Task>> taskList;

        taskList = newArrayList(transform(scriptItems, new Function<ScriptItem, TaskDef<Task>>() {
            @Nullable
            @Override
            public TaskDef<Task> apply(ScriptItem scriptItem) {
                return scriptExecContext.convertItemToTask(shellContext, scriptItem);
            }
        }));

        PreparationResult preparationResult = new BearRunner2(settings, factory).createRunContext();

        List<SessionContext> $s = preparationResult.getSessions();

        GlobalTaskRunner globalTaskRunner = new GlobalTaskRunner(global, taskList, $s, shellContext);

        //todo this should not be async - this message might be slow
        bearFX.sendMessageToUI(new RMIEventToUI("terminals", "onScriptStart", getHosts($s)));

        globalTaskRunner.startParties(global.localExecutor);

        return new RunResponse(globalTaskRunner, getHosts(preparationResult.getSessions()));
    }


    public static class BearScriptParseResult {
        List<ScriptItem> scriptItems;
        List<ScriptError> globalErrors;

        public BearScriptParseResult(List<ScriptItem> scriptItems, List<ScriptError> globalErrors) {
            this.scriptItems = scriptItems;
            this.globalErrors = globalErrors;
        }
    }


    static class BearScriptDirective {
        final String directive;
        final String[] words;
        final Map<String, String> params;
        final Optional<String> name;

        BearScriptDirective(String directive, Optional<String> name, String[] words, Map<String, String> params) {
            this.directive = directive;
            this.name = name;
            this.words = words;
            this.params = params;
        }
    }

    static final DirectiveParser directiveParser = new DirectiveParser();

    static BearScriptParseResult parseScript(String script, String initialPluginName) {
//        final List<String> currentScript = new ArrayList<String>();

        List<ScriptItem> scriptItems = new ArrayList<ScriptItem>();
        List<ScriptError> globalErrors = new ArrayList<ScriptError>();

//        String currentPluginName = initialPluginName;

        int lineIndex = 1;

        ScriptItem currentScriptItem = new ScriptItem(Optional.<String>absent(), initialPluginName, lineIndex);

        for (Iterator<String> iterator = LINE_SPLITTER.split(script).iterator(); iterator.hasNext(); lineIndex++) {
            String line = iterator.next();

            if (line.startsWith("#")) {
                continue;
            }

            if (line.startsWith(":") || line.startsWith("//!:")) {

                BearScriptDirective directive = directiveParser.parse(line);

                if (":use".equals(directive.directive)) {
                    String command = directive.words[0];
                    String pluginName = directive.words[1];

                    if ("shell".equals(command)) {
                        if (!currentScriptItem.isEmpty()) {
                            scriptItems.add(currentScriptItem);
                        }

                        currentScriptItem = new ScriptItem(directive.name, pluginName, lineIndex);
                    } else {
                        ui.error(new TextConsoleEventToUI("shell", "command not supported: <i>" + command + "</i><br>"));

                        globalErrors.add(new ScriptError(line, lineIndex, "command not supported: " + command));
                    }

                    continue;
                } else
                if(":ref".equals(directive.directive)){
                    scriptItems.add(scriptItemFromFileReference(directive));
                } else {
                    currentScriptItem.lines.add(line);
                }

                continue;
            }

            currentScriptItem.lines.add(line);
        }

        if (!currentScriptItem.isEmpty()) {
            scriptItems.add(currentScriptItem);
        }

        return new BearScriptParseResult(scriptItems, globalErrors);
    }

    static ScriptItem scriptItemFromFileReference(BearScriptDirective directive) {
        checkArgument(!directive.params.isEmpty(), "you need to provide params for script reference");

        try {
            String plugin = directive.params.get("plugin");

            String scriptString = null;
            String scriptName = null;

            Map<String, String> params = directive.params;

            if(params.containsKey("url")){
                String urlString = params.get("url");
                URL url = new URL(urlString);
                scriptString = Resources.asCharSource(url, Charsets.UTF_8).read();
                scriptName = FilenameUtils.getName(urlString);
            }else
            if(params.containsKey("file")){
                File file = new File(params.get("file"));

                scriptString = FileUtils.readFileToString(file);
                scriptName = file.getName();
            }


            if(params.containsKey("name")){
                scriptName = params.get("name");
            }

            if(plugin == null){
                plugin = FilenameUtils.getExtension(scriptName);

                checkArgument(!Strings.isNullOrEmpty(plugin), "could not detect plugin from filename: %s. please provide", scriptName);
            }

            checkArgument(!Strings.isNullOrEmpty(scriptName), "could detect not script name. please provide");

            return new ScriptItem(Optional.of(scriptName), plugin, LINE_SPLITTER.splitToList(scriptString), 0);
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }
    }

    public static List<RunResponse.Host> getHosts(List<SessionContext> $s) {
        return transform($s, new Function<SessionContext, RunResponse.Host>() {
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
        GlobalTaskRunner runContext;

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

        public RunResponse(GlobalTaskRunner globalTaskRunner, List<Host> hosts) {
            this.runContext = globalTaskRunner;
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
        public String shell;
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

    public static class GroovyScriptSupplier implements Supplier<BearScriptParseResult> {

        private final BearScriptParseResult parseResult;

        public GroovyScriptSupplier(GlobalContext global, String groovyScript, Optional<String> scriptName) {
            GroovyShellPlugin groovy = global.getPlugin(GroovyShellPlugin.class);

            this.parseResult = new BearScriptParseResult(
                Lists.newArrayList(
                    new ScriptItem(scriptName, groovy.cmdAnnotation(), LINE_SPLITTER.splitToList(groovyScript), 1)
                ),
                Collections.<ScriptError>emptyList());

        }

        @Override
        public BearScriptParseResult get() {
            return parseResult;
        }
    }

    public static class ParserScriptSupplier implements Supplier<BearScriptParseResult> {
        private final Plugin initialPlugin;
        private final String script;

        public ParserScriptSupplier(Plugin initialPlugin, String script) {
            this.initialPlugin = initialPlugin;
            this.script = script;
        }

        @Override
        public BearScriptParseResult get() {
            return parseScript(script, initialPlugin.cmdAnnotation());
        }
    }
}
