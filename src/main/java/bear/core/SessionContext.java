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
import bear.session.DynamicVariable;
import bear.session.SystemEnvironment;
import bear.task.TaskRunner;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class SessionContext {
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormat.forPattern("HH:mm:ss:SSS");
    //    public final GlobalContext globalContext;
    public final VariablesLayer sessionVariablesLayer;
    private final GlobalContext global;
    public final SystemEnvironment sys;
    public final TaskRunner runner;

    public SessionContext(GlobalContext global, SystemEnvironment sys, TaskRunner runner) {
        this.global = global;
        this.sys = sys;
        this.runner = runner;
        sys.set$(this);
        this.sessionVariablesLayer = SystemEnvironment.newSessionVars(global, sys);
        sessionVariablesLayer.putS(global.bear.sessionHostname, sys.getName());
    }

    public GlobalContext getGlobal() {
        return global;
    }

    public SessionContext(VariablesLayer sessionVariablesLayer) {
        this.sessionVariablesLayer = sessionVariablesLayer;
        sys = null;
        global = null;
        runner = null;
    }

    public <T> T var(DynamicVariable<T> varName) {
        return sessionVariablesLayer.get(this, varName);
    }

    public String joinPath(DynamicVariable<String> var, String path) {
        return sys.joinPath(var(var), path);
    }

    public String joinPath(String... paths) {
        return sys.joinPath(paths);
    }

    public <T> boolean isSet(Nameable<T> variable){
        final DynamicVariable<T> x = sessionVariablesLayer.getClosure(variable);

        return x != null && x.isSet();
    }

    public String threadName() {
        return sys.getName();
    }

    public boolean varB(Nameable<Boolean> var) {
        return sessionVariablesLayer.get(this, var, null);
    }

    public boolean varB(DynamicVariable<Boolean> var) {
        return sessionVariablesLayer.get(this, var);
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
}
