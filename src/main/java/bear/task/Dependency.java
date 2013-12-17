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

package bear.task;

import bear.plugins.sh.CommandLine;
import bear.plugins.sh.Script;
import bear.core.SessionContext;
import bear.session.Result;
import bear.vcs.CommandLineResult;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Dependency extends Task<TaskDef> {

    String name;
    String actual;

    List<Check> checks = new ArrayList<Check>();

    public static DependencyResult checkDeps(Iterable<Dependency> transform) {
        DependencyResult r = new DependencyResult(Result.OK);

        for (Dependency dependency : transform) {
            r.join(dependency.checkDeps());
        }

        return r;
    }

    public Dependency addCommands(String... commands) {
        for (String command : commands) {
            add(new Command(
                $.sys.line().addRaw(command),
                new Predicate<CharSequence>() {
                    @Override
                    public boolean apply(CharSequence input) {
                        return !input.toString().contains("command not found");
                    }
                },
                "command '" + command + "' not found")
            );
        }

        return this;
    }


    public abstract class Check {

        public abstract boolean check();

        public abstract String message();
    }

    public static final Dependency NONE = new Dependency("NONE");

    public class Directory extends Check {
        String path;
        boolean checkWritable;

        public Directory(String path) {
            this.path = path;
        }

        @Override
        public boolean check() {
            return $.sys.sendCommand($.sys.line().addRaw(
                "test -d " + path
                    + (checkWritable ? " && test -x " + path : ""))).result.ok();
        }

        @Override
        public String message() {
            return "'" + path + "' is not a " + (checkWritable ? "" : "writable ") +
                "dir or does not exist";
        }
    }


    public Dependency(String name) {
        super(null, null, null);
        this.name = name;
    }

    public Dependency(String name, SessionContext $) {
        super(null, null, $);

        this.name = name;
    }

    public Dependency(TaskDef def, String name, SessionContext $, Task<TaskDef> parent) {
        super(parent,def, $);

        this.name = name;
    }

    public class File extends Check {
        String path;
        boolean checkWritable;

        public File(String path) {
            this.path = path;
        }

        @Override
        public boolean check() {
            return $.sys.sendCommand($.sys.line().addRaw(
                "test -t " + path
                    + (checkWritable ? (" && test -w " + path) : ""))).result.ok();
        }

        @Override
        public String message() {
            return "'" + path + "' is not a " + (checkWritable ? "" : "writable ") +
                "dir or does not exist";
        }
    }

    public class Command extends Check {
        Script script;
        Predicate<CharSequence> matcher;
        private final String message;

        public Command(CommandLine line, Predicate<CharSequence> matcher, String message) {
            this.matcher = matcher;
            this.message = message;
            script = $.sys.script().add(line);
        }

        public Command(CommandLine line, String command, String version) {
            this.matcher = Predicates.containsPattern(version);
            message = "'" + command + "' must be of version " + version;
            script = $.sys.script().add(line);
        }

        public Command(String command, String version) {
            this($.sys.line().addRaw(command), command, version);
        }

        @Override
        public boolean check() {
            CommandLineResult run = script.timeoutSec(30).run();

            return run.ok() && matcher.apply(run.text);

        }

        @Override
        public String message() {
            return message;
        }
    }


    public Dependency add(Check check) {
        checks.add(check);
        return this;
    }

    @Override
    protected DependencyResult exec(SessionRunner runner, Object input) {
        return checkDeps();
    }

    public DependencyResult checkDeps() {
        DependencyResult result = new DependencyResult(Result.OK);

        for (Check check : checks) {
            if (!check.check()) {
                result.add(check.message());
            }
        }

        if (!result.messages.isPresent()) {
            return result;
        }

        result.setResult(Result.ERROR);

        return result;
    }

    public Dependency setActual(String actual) {
        this.actual = actual;
        return this;
    }
}
