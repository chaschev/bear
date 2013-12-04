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

import bear.console.ConsoleCallback;
import bear.core.SessionContext;
import bear.plugins.sh.SystemSession;
import bear.session.Result;
import bear.vcs.CommandLineResult;
import chaschev.lang.OpenStringBuilder;
import com.google.common.base.Function;

import java.util.ArrayList;
import java.util.List;

/**
 * todo: configure optional chaining via &&?
 *
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Script <T extends CommandLineResult, CHILD extends Script>{
    public String cd = ".";

    protected SystemSession sys;

    public List<CommandLine<T, CHILD>> lines = new ArrayList<CommandLine<T, CHILD>>();

    protected Function<String, T> parser;

    protected int timeoutMs = -1;

    public Script(SystemSession sys) {
        this.sys = sys;
    }

    public CommandLine<T, CHILD> line() {
        final CommandLine line = sys.line(this);

        add(line);

        return line;
    }

    public CHILD line(CommandLine<T, CHILD> line) {
        add(line);

        return (CHILD) this;
    }

    public CHILD add(CommandLine<T, CHILD> commandLine) {
        lines.add(commandLine);

        if(timeoutMs !=-1){
            commandLine.timeoutMs(timeoutMs);
        }

        return (CHILD) this;
    }

    public CHILD cd(String cd) {
        this.cd = cd;
        return (CHILD) this;
    }


    public T run() {
        return (T) sys.run(this);
    }

    public T run(ConsoleCallback callback) {
        return sys.run(this, callback);
    }

    public CHILD setParser(Function<String, T> parser) {
        this.parser = parser;
        return (CHILD) this;
    }

    public T parseResult(String text, SessionContext $, String script) {
        return (T) parseWithParser(parser, text, $, script);
    }

    static CommandLineResult parseWithParser(Function<String, ? extends CommandLineResult> parser, String text, SessionContext $, String script) {
        final CommandLineResult obj;

        if (parser != null) {
            obj = parser.apply(text);
            obj.text = text;
        }else{
            obj = new CommandLineResult(script, text, Result.OK);
        }

        return obj.validate($);
    }

    public CHILD timeoutMin(int min) {
        return timeoutSec(60 * min);
    }

    public CHILD timeoutSec(int sec) {
        return timeoutMs(1000 * sec);
    }

    public CHILD timeoutMs(int ms) {
        timeoutMs = ms;

        for (CommandLine line : lines) {
            if(line.getTimeoutMs() == 0){
                line.timeoutMs(ms);
            }
        }

        return (CHILD) this;
    }

    public boolean isDefaultDir() {
        return cd == null || ".".equals(cd);
    }

    public String firstLineAsText(){
        return lines.isEmpty() ? "<empty>" : lines.get(0).asText(false);
    }

    public String asTextScript() {
        OpenStringBuilder sb = new OpenStringBuilder();

        for (CommandLine<T, CHILD> line : lines) {
            sb.append(line.asText(true)).trim();
            if(sb.charAt(sb.length() - 1) != ';') {
                sb.append(";\n");
            }else{
                sb.append('\n');
            }
        }

        return sb.trim().toString();
    }

    public Script timeoutForInstallation() {
        return timeoutMs(sys.$(sys.getBear().installationTimeoutMs));
    }

    public Script timeoutForBuild() {
        return timeoutMs(sys.$(sys.getBear().buildTimeoutMs));
    }

    public Script timeoutShort() {
        return timeoutMs(sys.$(sys.getBear().shortTimeoutMs));
    }
}
