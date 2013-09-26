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

package cap4j.cli;

import cap4j.scm.CommandLineResult;
import cap4j.session.Result;
import cap4j.session.SystemEnvironment;
import com.google.common.base.Function;

import java.util.ArrayList;
import java.util.List;

/**
 * todo: configure optional chaining via &&?
 *
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Script <T extends CommandLineResult>{


    public static class StubScript<T extends CommandLineResult> extends Script<T>{
        private final T result;

        public StubScript(SystemEnvironment system, T result) {
            super(system);

            this.result = result;
        }

        @Override
        public CommandLineResult run() {
            return result;
        }

        @Override
        public T parseResult(String text) {
            return result;
        }
    }

    public String cd = ".";

    protected SystemEnvironment system;

    public List<CommandLine> lines = new ArrayList<CommandLine>();

    protected Function<String, T> parser;

    public Script(SystemEnvironment system) {
        this.system = system;
    }

    public CommandLine line() {
        final CommandLine line = system.line(this);

        lines.add(line);

        return line;
    }

    public Script line(CommandLine line) {
        lines.add(line);

        return this;
    }

    public Script add(CommandLine commandLine) {
        lines.add(commandLine);

        return this;
    }

    public Script cd(String cd) {
        this.cd = cd;
        return this;
    }


    public CommandLineResult run() {
        return system.run(this);
    }

    public Script<T> setParser(Function<String, T> parser) {
        this.parser = parser;
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

    public Script<T> timeoutMs(int ms) {
        for (CommandLine line : lines) {
            if(line.timeoutMs == 0){
                line.timeoutMs = ms;
            }
        }

        return this;
    }
}
