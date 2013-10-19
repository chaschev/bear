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

import bear.cli.CommandLine;
import bear.plugins.AbstractContext;
import bear.session.*;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.TaskResult;
import bear.task.TaskRunner;
import bear.task.exec.CommandExecutionEntry;
import bear.task.exec.TaskExecutionContext;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import static bear.session.Variables.dynamic;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class SessionContext extends AbstractContext{
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormat.forPattern("HH:mm:ss:SSS");
    //    public final GlobalContext globalContext;
    private GlobalContext global;
    public final SystemEnvironmentPlugin.SystemSessionDef sysDef;
    public GenericUnixLocalEnvironmentPlugin localSysEnv;
    public GenericUnixRemoteEnvironmentPlugin remoteSysEnv;
    public final SystemSession sys;
    public final TaskRunner runner;
    public Bear bear;
    public Address address;

    public class ExecutionContext{
        public final DateTime startedAt = new DateTime();
        public final DynamicVariable<StringBuilder> text = dynamic(StringBuilder.class).setDesc("text appended in session").defaultTo(new StringBuilder(8192));
        public final DynamicVariable<String> textAppended = dynamic(String.class).setDesc("text appended in session").defaultTo("");
        public final DynamicVariable<TaskExecutionContext> rootExecutionContext = dynamic(TaskExecutionContext.class);
        public final DynamicVariable<Task> currentTask = dynamic(Task.class);
        public final DynamicVariable<CommandExecutionEntry> currentCommand = dynamic(CommandExecutionEntry.class);

        public void textAdded(String textAdded) {
            StringBuilder sb = text.apply(SessionContext.this);
            sb.append(textAdded);
            text.fireExternalModification(null, sb);
            textAppended.defaultTo(textAdded);
        }
    }

    protected Task<TaskDef> currentTask;

    protected ExecutionContext executionContext = new ExecutionContext();

    public SessionContext(GlobalContext global, Address address, TaskRunner runner) {
        super(global);

        ///this can be extracted into newContext(aClass, parent, Object... fields)
        this.runner = runner;

        global.wire(this);       //sets bear, global and the SystemEnvironment plugin =)

        layer.put(bear.sessionHostname, address.getName());
        layer.put(bear.sessionAddress, address.getAddress());

        this.address = wire(address);

        ////////
        // this can be extracted into init

        sysDef = ((address instanceof SshAddress) ? remoteSysEnv : localSysEnv).getTaskDef();
        sys = sysDef.newSession(this, null);


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

    public TaskResult run(TaskDef task) {
        return runner.run(task);
    }

    public Task<TaskDef> getCurrentTask() {
        return currentTask;
    }

    public void setCurrentTask(Task<TaskDef> currentTask) {
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

    public TaskRunner getRunner() {
        return runner;
    }

    @Override
    public GlobalContext getGlobal() {
        return global;
    }

    public String getName() {
        return sys.getName();
    }

    public String concat(Object... varsAndStrings) {
        return Variables.concat(this, varsAndStrings);
    }
}
