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

package bear.cli;

import bear.console.AbstractConsoleCommand;
import bear.core.SessionContext;
import bear.plugins.sh.SystemSession;
import bear.task.BearException;
import bear.vcs.CommandLineOperator;
import bear.vcs.CommandLineResult;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class CommandLine<T extends CommandLineResult, SCRIPT extends Script> extends AbstractConsoleCommand<T> {
    public String cd = ".";

    public List strings = new ArrayList(4);

    protected Function<String, T> parser;

    /**
     * Null when used outside of a script.
     */
    @Nullable
    protected SCRIPT script;

    protected SystemSession sys;

    protected CommandLine(SystemSession sys) {
        this.sys = sys;
    }

    protected CommandLine(SCRIPT script) {
        this.script = script;
        this.sys = script.sys;
    }

    public CommandLine<T, SCRIPT> a(String... strings) {
        for (String s : strings) {
            if(!StringUtils.isBlank(s)){
                this.strings.add(s);
            }
        }
        return this;
    }

    public CommandLine<T, SCRIPT> a(List<String> strings) {
        for (String s : strings) {
            if(!StringUtils.isBlank(s)){
                this.strings.add(s);
            }
        }
        return this;
    }

    public CommandLine<T, SCRIPT> addSplit(String s) {
        Collections.addAll(strings, s.split("\\s+"));
        return this;
    }

    public CommandLine<T, SCRIPT> p(Map<String, String> params) {
        for (Map.Entry<String, String> e : params.entrySet()) {
            strings.add(" --" + e.getKey() + "=" + e.getValue() + " ");
        }
        return this;
    }

    public T parseResult(SessionContext $, String text) {
        return (T) Script.parseWithParser(parser, text, $, asText(false));
    }

    public CommandLine<T, SCRIPT> setParser(Function<String, T> parser) {
        this.parser = parser;
        return this;
    }

    public CommandLine<T, SCRIPT> cd(String cd) {
        this.cd = cd;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("run \"");
        Joiner.on("\" \"").appendTo(sb, strings);
        sb.append("\" in dir '").append(cd).append('\'');
        sb.append(", timeout: ").append(timeoutMs).append("ms");
        return sb.toString();
    }

    public CommandLine<T, SCRIPT> semicolon() {
        strings.add(new CommandLineOperator(";"));
        return this;
    }

    public CommandLine<T, SCRIPT> redirectFrom(String path) {
        strings.add(new CommandLineOperator("<" + path));
        return this;
    }

    public CommandLine<T, SCRIPT> redirectTo(String path) {
        strings.add(new CommandLineOperator(">" + path));
        return this;
    }


    public CommandLine<T, SCRIPT> addRaw(String format, String... args) {
        return addRaw(format, false, args);
    }

    public CommandLine<T, SCRIPT> addRaw(String format, boolean force, String... args) {
        if(format.contains("rm ") && !force){
            throw new BearException("rm in raw mode is forbidden. Use rmLine(...) or rm(...) to avoid deleting system libs.");
        }
        strings.add(new CommandLineOperator(String.format(format, args)));
        return this;
    }

    public CommandLine<T, SCRIPT> addRaw(String s) {
        strings.add(new CommandLineOperator(s));
        return this;
    }

    public CommandLine<T, SCRIPT> stty() {
        strings.add(new CommandLineOperator("stty -echo;"));
        return this;
    }

    public CommandLine<T, SCRIPT> sudo() {
        strings.add(new CommandLineOperator("stty -echo; sudo "));
        return this;
    }

    public CommandLine<T, SCRIPT> timeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;

        return this;
    }

    public CommandLine<T, SCRIPT> timeoutSec(int timeoutSec) {
        return timeoutMs(1000 * timeoutSec);
    }

    public CommandLine<T, SCRIPT> timeoutMin(int timeoutMin) {
        return timeoutSec(60 * timeoutMin);
    }

    public CommandLine<T, SCRIPT> bash() {
        strings.add(new CommandLineOperator("bash -c"));
        return this;
    }

    public CommandLine<T, SCRIPT> setVar(String k, String v) {
        strings.add(new CommandLineOperator("export " + k + "=" + v + "; "));
        return this;
    }

    public CommandLine pipe() {
        return addRaw(" | ");
    }

    public SCRIPT build() {
        return script;
    }

    public void setScript(@Nullable SCRIPT script) {
        this.script = script;
    }

    @Override
    public String asText(boolean forExecution) {
        StringBuilder sb = new StringBuilder(128);
        CommandLine line = this;

        if (line.cd != null && !".".equals(line.cd)) {
            sb.append("cd ").append(line.cd).append(" && ");
        }

        if (forExecution && sys.isSudo()) {
            sb.append("stty -echo && sudo ");
        }

        List strings = line.strings;

        for (Object string : strings) {
            if (string instanceof CommandLineOperator) {
                String s = string.toString();
                if(forExecution || !s.contains("export ")){
                    sb.append(string);
                }
            } else {
                if(forExecution) sb.append('"');
                sb.append(string);
                if(forExecution) sb.append('"');
            }

            sb.append(" ");
        }

        return sb.toString();
    }

    public boolean isDefaultDir() {
        return cd == null || ".".equals(cd);
    }

    public CommandLine<T, SCRIPT> timeoutForInstallation() {
        return timeoutMs(sys.$(sys.getBear().installationTimeoutMs));
    }

    public CommandLine<T, SCRIPT> timeoutForBuild() {
        return timeoutMs(sys.$(sys.getBear().buildTimeoutMs));
    }

    public CommandLine<T, SCRIPT> timeoutShort() {
        return timeoutMs(sys.$(sys.getBear().shortTimeoutMs));
    }

    public CommandLine sudo(boolean sudo){
        if(sudo) return sudo();
        return this;
    }

    public CommandLine sudoOrStty(boolean sudo){
        if(sudo) return sudo();
        return stty();
    }
}
