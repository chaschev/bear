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
import bear.task.BearException;
import bear.vcs.CommandLineResult;
import bear.session.Result;
import bear.session.SystemEnvironment;
import bear.vcs.VcsCLIPlugin;
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
public abstract class CommandLine<T extends CommandLineResult> extends AbstractConsoleCommand<T> {
    public String cd = ".";

    public List strings = new ArrayList(4);

    protected Function<String, T> parser;

    /**
     * Null when used outside of a script.
     */
    @Nullable
    protected Script script;

    protected SystemEnvironment sys;

    protected CommandLine(SystemEnvironment sys) {
        this.sys = sys;
    }

    protected CommandLine(Script script) {
        this.script = script;
        this.sys = script.sys;
    }

    public CommandLine a(String... strings) {
        for (String s : strings) {
            if(!StringUtils.isBlank(s)){
                this.strings.add(s);
            }
        }
        return this;
    }

    public CommandLine a(List<String> strings) {
        for (String s : strings) {
            if(!StringUtils.isBlank(s)){
                this.strings.add(s);
            }
        }
        return this;
    }

    public CommandLine addSplit(String s) {
        Collections.addAll(strings, s.split("\\s+"));
        return this;
    }

    public CommandLine p(Map<String, String> params) {
        for (Map.Entry<String, String> e : params.entrySet()) {
            strings.add(" --" + e.getKey() + "=" + e.getValue() + " ");
        }
        return this;
    }

    public T parseResult(String text) {
        if (parser != null) {
            final T obj = parser.apply(text);
            obj.text = text;
            return obj;
        }

        return (T) new CommandLineResult(text, Result.OK);
    }

    public CommandLine<T> setParser(Function<String, T> parser) {
        this.parser = parser;
        return this;
    }

    public CommandLine<T> cd(String cd) {
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

    public CommandLine<T> semicolon() {
        strings.add(new VcsCLIPlugin.CommandLineOperator(";"));
        return this;
    }

    public CommandLine<T> redirectFrom(String path) {
        strings.add(new VcsCLIPlugin.CommandLineOperator("<" + path));
        return this;
    }

    public CommandLine<T> redirectTo(String path) {
        strings.add(new VcsCLIPlugin.CommandLineOperator(">" + path));
        return this;
    }


    public CommandLine<T> addRaw(String format, String... args) {
        return addRaw(format, false, args);
    }

    public CommandLine<T> addRaw(String format, boolean force, String... args) {
        if(format.contains("rm ") && !force){
            throw new BearException("rm in raw mode is forbidden. Use rmLine(...) or rm(...) to avoid deleting system libs.");
        }
        strings.add(new VcsCLIPlugin.CommandLineOperator(String.format(format, args)));
        return this;
    }

    public CommandLine<T> addRaw(String s) {
        strings.add(new VcsCLIPlugin.CommandLineOperator(s));
        return this;
    }

    public CommandLine<T> stty() {
        strings.add(new VcsCLIPlugin.CommandLineOperator("stty -echo;"));
        return this;
    }

    public CommandLine<T> sudo() {
        strings.add(new VcsCLIPlugin.CommandLineOperator("stty -echo; sudo "));
        return this;
    }

    public CommandLine<T> timeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;

        return this;
    }

    public CommandLine<T> timeoutSec(int timeoutSec) {
        return timeoutMs(1000 * timeoutSec);
    }

    public CommandLine<T> timeoutMin(int timeoutMin) {
        return timeoutSec(60 * timeoutMin);
    }

    public CommandLine<T> bash() {
        strings.add(new VcsCLIPlugin.CommandLineOperator("bash -c"));
        return this;
    }

    public CommandLine<T> setVar(String k, String v) {
        strings.add(new VcsCLIPlugin.CommandLineOperator("export " + k + "=" + v + "; "));
        return this;
    }

    public CommandLine pipe() {
        return addRaw(" | ");
    }

    public Script build() {
        return script;
    }

    public void setScript(@Nullable Script script) {
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
            if (string instanceof VcsCLIPlugin.CommandLineOperator) {
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
}
