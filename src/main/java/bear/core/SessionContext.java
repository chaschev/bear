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

import bear.console.ConsoleCallback;
import bear.plugins.sh.CommandLine;
import bear.context.AbstractContext;
import bear.plugins.Plugin;
import bear.plugins.sh.GenericUnixLocalEnvironmentPlugin;
import bear.plugins.sh.GenericUnixRemoteEnvironmentPlugin;
import bear.plugins.sh.SystemEnvironmentPlugin;
import bear.plugins.sh.SystemSession;
import bear.session.Address;
import bear.session.DynamicVariable;
import bear.session.SshAddress;
import bear.session.Variables;
import bear.task.*;
import com.google.common.base.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.HashMap;
import java.util.Map;

import static bear.session.Variables.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class SessionContext extends AbstractContext {


    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormat.forPattern("HH:mm:ss:SSS");
    //    public final GlobalContext globalContext;
    public final SystemEnvironmentPlugin.SystemSessionDef sysDef;
    public GenericUnixLocalEnvironmentPlugin localSysEnv;
    public GenericUnixRemoteEnvironmentPlugin remoteSysEnv;
    public final SystemSession sys;
    public final SessionTaskRunner runner;
    public Bear bear;
    public Address address;
//    protected CompositeTaskRunContext taskRunContext;
    public static final org.apache.logging.log4j.Logger ui = LogManager.getLogger("fx");

    protected Task<?> currentTask;

    protected ExecutionContext executionContext = new ExecutionContext();

    protected GlobalTaskRunner globalTaskRunner;

    public final String id = randomId();
    protected Thread thread;

    public static String randomId() {
        return RandomStringUtils.randomAlphanumeric(6);
    }

    public Thread getThread() {
        return thread;
    }

    public void setThread(Thread thread) {
        this.thread = thread;
        thread.setName(threadName());
    }

    public void whenPhaseStarts(BearScriptPhase phase, BearScriptRunner.ShellSessionContext shellSessionContext){
        StringBuilder phaseSB = executionContext.phaseText.getDefaultValue();
        phaseSB.setLength(0);
        executionContext.phaseText.fireExternalModification();

        executionContext.phaseName = phase.getName();
        executionContext.phaseId.defaultTo(phase.id);
    }

    public void whenSessionComplete(GlobalTaskRunner globalTaskRunner) {
        thread = null;

        DynamicVariable<TaskExecutionContext> execCtx = executionContext.rootExecutionContext;

        boolean isOk = execCtx.getDefaultValue().taskResult.ok();

        globalTaskRunner.stats.getDefaultValue().addArrival(isOk);
        globalTaskRunner.stats.fireExternalModification();

        globalTaskRunner.arrivedCount.getDefaultValue().incrementAndGet();
        globalTaskRunner.arrivedCount.fireExternalModification();
    }

    public void cancel(){
        if(thread == null){
            throw new IllegalStateException("not running or already cancelled");
        }

        thread.interrupt();
    }

    public void terminate() {
        if(thread == null){
            throw new IllegalStateException("not running or already cancelled");
        }

        ui.info("terminating...");

        thread.interrupt();
    }

    public boolean isRunning() {
        return executionContext.isRunning();
    }

    public ConsoleCallback sshCallback() {
        return SystemEnvironmentPlugin.println(var(bear.sshPassword));
    }


    public static class ExecutionHistoryEntry{

    }

    public static class ExecutionHistory{
        protected Map<TaskDef<Task>, TaskResult> resultMap = new HashMap<TaskDef<Task>, TaskResult>();


    }

    public class ExecutionContext{
        public final DateTime startedAt = new DateTime();
        public final DynamicVariable<String> phaseId = undefined();
        public final DynamicVariable<StringBuilder> text = newVar(new StringBuilder(8192)).desc("text appended in session");
        public final DynamicVariable<StringBuilder> phaseText = newVar(new StringBuilder(8192)).desc("text appended in session");
        public final DynamicVariable<String> textAppended = dynamic(String.class).desc("text appended in session").defaultTo("");
        public final DynamicVariable<TaskExecutionContext> rootExecutionContext = dynamic(TaskExecutionContext.class);
        public final DynamicVariable<Task> currentTask = dynamic(Task.class);
        public final DynamicVariable<CommandContext> currentCommand = dynamic(CommandContext.class);
        public String phaseName;

        public void textAdded(String textAdded) {
            updateBuffer(textAdded, text);
            updateBuffer(textAdded, phaseText);
            textAppended.defaultTo(textAdded);
        }

        private void updateBuffer(String textAdded, DynamicVariable<StringBuilder> sbVar) {
            StringBuilder sb = sbVar.apply(SessionContext.this);
            sb.append(textAdded);
            sbVar.fireExternalModification(null, sb);
        }

        public boolean isRunning(){
            return rootExecutionContext.getDefaultValue().isRunning();
        }
    }

    public SessionContext(GlobalContext global, Address address, SessionTaskRunner runner) {
        super(global, address.getName());

        ///this can be extracted into newContext(aClass, parent, Object... fields)
        this.runner = runner;

        global.wire(this);       //sets bear, global and the SystemEnvironment plugin =)
        this.global = global;

        layer.put(bear.sessionHostname, address.getName());
        layer.put(bear.sessionAddress, address.getAddress());

        this.address = wire(address);

        ////////
        // this can be extracted into init

        sysDef = ((address instanceof SshAddress) ? remoteSysEnv : localSysEnv).getTaskDef();
        sys = sysDef.singleTaskSupplier().createNewSession(this, null, sysDef);

        this.setName(address.getName());

        if (address instanceof SshAddress) {
            SshAddress a = (SshAddress) address;

            layer.putConst(bear.sshUsername, a.username);
            layer.putConst(bear.sshPassword, a.password);
        }

        runner.set$(this);
    }

    public String joinPath(DynamicVariable<String> var, String path) {
        return sys.joinPath(var(var), path);
    }

    public String joinPath(String... paths) {
        return sys.joinPath(paths);
    }

    public String threadName() {
        return sys.getName();
    }

    public CommandLine newCommandLine() {
        return sys.newCommandLine();
    }


    public void log(String s, Object... params) {
        if (!s.endsWith("%n") && !s.endsWith("\n")) {
            s += "\n";
        }

        System.out.printf(s, params);
    }

    public void warn(String s, Object... params) {
        logLevel(s, "WARN", params);
    }

    public void error(String s, Object... params) {
        logLevel(s, "ERROR", params);
    }

    private void logLevel(String s, String level, Object[] params) {
        // and here's how to get the String representation

        if (!s.endsWith("%n") && !s.endsWith("\n")) {
            s += "\n";
        }

        System.out.printf(new DateTime().toString(TIME_FORMATTER) + " " + level + " " + s, params);
    }

    public TaskResult run(TaskDef... tasks) {
        return runner.run(tasks);
    }

    public Task<?> getCurrentTask() {
        return currentTask;
    }

    public void setCurrentTask(Task<?> currentTask) {
        if(currentTask.isRootTask()){
            executionContext.rootExecutionContext.defaultTo(currentTask.getExecutionContext());
        }

        executionContext.currentTask.defaultTo(currentTask);
        this.currentTask = currentTask;
    }

    public void logOutput(String textAdded) {
        System.out.print(textAdded);

        executionContext.textAdded(textAdded);
    }

    public ExecutionContext getExecutionContext() {
        return executionContext;
    }

    public SystemSession getSys() {
        return sys;
    }

    public SessionTaskRunner getRunner() {
        return runner;
    }

    @Override
    public GlobalContext getGlobal() {
        return (GlobalContext) global;
    }

    public String getName() {
        return sys.getName();
    }

    public String concat(Object... varsAndStrings) {
        return Variables.concat(this, varsAndStrings);
    }

    public void setGlobalTaskRunner(GlobalTaskRunner globalTaskRunner) {
        this.globalTaskRunner = globalTaskRunner;
    }

    public <T extends Plugin> T plugin(Class<T> pluginClass) {
        return getGlobal().getPlugin(pluginClass);
    }

    public TaskResult runSession(Task<?> taskSession) {
        return runner.runSession(taskSession);
    }

    public TaskResult runSession(Task<?> taskSession, Object input) {
        return runner.runSession(taskSession, input);
    }

    public Optional<TaskResult> findResult(TaskDef<Task> def){
        return executionContext.rootExecutionContext.getDefaultValue().findResult(def);
    }

    // returns last available result, null if none of the tasks finished
    public Optional<TaskResult> lastResult(){
        return executionContext.rootExecutionContext.getDefaultValue().lastResult();
    }
}
