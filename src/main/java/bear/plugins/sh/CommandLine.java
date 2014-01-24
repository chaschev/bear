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

package bear.plugins.sh;

import bear.console.AbstractConsoleCommand;
import bear.console.ConsoleCallback;
import bear.core.SessionContext;
import bear.session.Result;
import bear.task.BearException;
import bear.vcs.CommandLineOperator;
import bear.vcs.CommandLineResult;
import com.google.common.base.Joiner;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static bear.vcs.CommandLineResult.error;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class CommandLine<T extends CommandLineResult<?>, SCRIPT extends Script> extends AbstractConsoleCommand<T> {
    public String cd = ".";

    public List strings = new ArrayList(4);

    protected ResultParser<T> parser;
    protected ResultValidator validator = new ResultValidator() {
        @Override
        public void validate(String script, String output) {
            CommandBuilder.defaultValidation(script, output);
        }
    };

    /**
     * Null when used outside of a script.
     */
    @Nullable
    //todo change to Script
    protected SCRIPT script;

    protected SystemSession sys;

    protected CommandLine(SystemSession sys) {
        this.sys = sys;
    }

    protected CommandLine(SCRIPT script) {
        this.script = script;
        this.sys = script.sys;
    }

    static CommandLineResult<?> parseWithParser(ResultParser<? extends CommandLineResult<?>> parser, ResultValidator validator, String commandOutput, SessionContext $, String script) {
        CommandLineResult<?> obj = null;

        if(validator != null){
            try {
                validator.validate(script, commandOutput);
            } catch (Exception e) {
                return parser == null ? error(e) : parser.error(e);
            }
        }
        if (parser != null) {
            obj = parser.parse(script,commandOutput);
            obj.output = commandOutput;
        }else{
            obj = new CommandLineResult(script, commandOutput, Result.OK);
        }



        return obj;
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
        return (T) parseWithParser(parser, validator, text, $, asText(false));
    }

    public CommandLine<T, SCRIPT> setParser(ResultParser<T> parser) {
        this.parser = parser;
        return this;
    }

    public CommandLine<T, SCRIPT> setValidator(ResultValidator validator) {
        this.validator = validator;
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

    public CommandLine<T, SCRIPT> timeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;

        return this;
    }

    public CommandLine<T, SCRIPT> timeoutSec(long timeoutSec) {
        return timeoutMs(1000 * timeoutSec);
    }

    public CommandLine<T, SCRIPT> timeoutMin(long timeoutMin) {
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
        if(script == null){
            throw new IllegalStateException("you need to create a script first");
        }

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

//        if (forExecution && sys.isSudo()) {
//            sb.append("stty -echo && sudo ");
//        }

        List strings = line.strings;

        for (Object o : strings) {
            if (o instanceof CommandLineOperator) {
                String s = o.toString();
                if(forExecution || !s.contains("export ")){
                    sb.append(o);
                }
            } else {
                if(forExecution) {
                    sb.append('"');
                    if(o instanceof String){
                        // will make: rm "/foo/ba"*"" -> rm "/foo/ba"*
                        sb.append(extractWildcards((String)o));
                    }else{
                        sb.append(o);
                    }
                    sb.append('"');
                }else{
                    sb.append(o);
                }
            }

            sb.append(" ");
        }

        return sb.toString();
    }

    @Override
    public CommandLine<T, SCRIPT> setCallback(ConsoleCallback callback) {
        super.setCallback(callback);
        return this;
    }

    private String extractWildcards(String s) {
        s = s.replace("*", "\"*\"");

        return s;
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

    public CommandLine sshCallback(SessionContext $) {
        setCallback($.sshCallback());
        return this;
    }
}
