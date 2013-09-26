package cap4j.core;

import cap4j.cli.CommandLine;
import cap4j.cli.Script;
import cap4j.session.Result;
import cap4j.task.Task;
import cap4j.task.TaskRunner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Dependency extends Task<DependencyResult>{

    public abstract class Check{

        public abstract boolean check();

        public abstract String message();
    }

    public class Directory extends Check{
        String path;
        boolean checkWritable;

        public Directory(String path) {
            this.path = path;
        }

        @Override
        public boolean check(){
            return $.system.run($.system.line().addRaw(
                "test -d " + path
                + (checkWritable ? " && test -x " + path: ""))).result.ok();
        }

        @Override
        public String message() {
            return "'" + path + "' is not a " + (checkWritable ? "": "writable ") +
                "dir or does not exist";
        }
    }

    public Dependency() {
    }

    public Dependency(String name) {
        super(name);
    }

    public class File extends Check{
        String path;
        boolean checkWritable;

        public File(String path) {
            this.path = path;
        }

        @Override
        public boolean check(){
            return $.system.run($.system.line().addRaw(
                "test -t " + path
                    + (checkWritable ? (" && test -w " + path): ""))).result.ok();
        }

        @Override
        public String message() {
            return "'" + path + "' is not a " + (checkWritable ? "": "writable ") +
                "dir or does not exist";
        }
    }

    public class Command extends Check{
        Script script;
        Predicate<CharSequence> matcher;
        private final String message;

        public Command(CommandLine line, Predicate<CharSequence> matcher, String message) {
            this.matcher = matcher;
            this.message = message;
            script = $.system.script().add(line);
        }

        public Command(CommandLine line, String command, String version) {
            this.matcher = Predicates.containsPattern(version);
            message = "'" + command + "' must be of version " + version;
            script = $.system.script().add(line);
        }

        public Command(String command, String version) {
            this($.system.line().addRaw(command), command, version);
        }

        @Override
        public boolean check(){
            return matcher.apply(script.run().text);
        }

        @Override
        public String message() {
            return message;
        }
    }

    List<Check> checks = new ArrayList<Check>();

    public Dependency add(Check check) {
        checks.add(check);
        return this;
    }

    @Override
    protected DependencyResult run(TaskRunner runner) {
        return checkDeps();
    }

    public DependencyResult checkDeps() {
        DependencyResult result = new DependencyResult(Result.OK);

        for (Check check : checks) {
            if (!check.check()) {
                result.add(check.message());
            }
        }

        if (result.messages == null) {
            return result;
        }

        result.result = Result.ERROR;

        return result;
    }


}
