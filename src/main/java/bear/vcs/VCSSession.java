package bear.vcs;

import bear.cli.CommandLine;
import bear.console.ConsoleCallback;
import bear.core.SessionContext;
import bear.plugins.sh.SystemEnvironmentPlugin;
import bear.session.DynamicVariable;
import bear.task.SessionTaskRunner;
import bear.task.Task;
import bear.task.TaskDef;
import bear.task.TaskResult;
import com.google.common.base.Function;

import java.util.Map;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public abstract class VCSSession extends Task<TaskDef> {
    VCSSession(Task<TaskDef> parent, TaskDef def, SessionContext $) {
        super(parent, def, $);
    }

    @Override
    protected TaskResult exec(SessionTaskRunner runner) {
        throw new UnsupportedOperationException("VCS task cannot be run");
    }

    public VCSScript<?> checkout(String revision, String destination) {
        return checkout(revision, destination, VcsCLIPlugin.emptyParams());
    }

    public VCSScript<?> checkout(String revision, String destination, Map<String, String> params) {
        throw new UnsupportedOperationException("todo");
    }

    public VCSScript<?> sync(String revision, String destination) {
        return sync(revision, destination, VcsCLIPlugin.emptyParams());
    }

    public VCSScript<?> sync(String revision, String destination, Map<String, String> params) {
        throw new UnsupportedOperationException("todo");
    }

    public VCSScript<?> export(String revision, String destination, Map<String, String> params) {
        throw new UnsupportedOperationException("todo");
    }

    public VCSScript<?> diff(String rFrom, String rTo, Map<String, String> params) {
        throw new UnsupportedOperationException("todo");
    }

    public VCSScript<? extends BranchInfoResult> queryRevision(String revision) {
        return queryRevision(revision, VcsCLIPlugin.emptyParams());
    }



    /**
     * f the given revision represents a "real" revision, this should
     * simply return the revision value. If it represends a pseudo-revision
     * (like Subversions "HEAD" identifier), it should yield a string
     * containing the commands that, when executed will return a string
     * that this method can then extract the real revision from.
     */
    public VCSScript<? extends BranchInfoResult> queryRevision(String revision, Map<String, String> params) {
        throw new UnsupportedOperationException("todo");
    }

    public String nextRevision(String r) {
        return r;
    }

    public String command() {
        throw new UnsupportedOperationException("todo VcsCLIContext.command");
    }

    public VCSScript<?> log(String rFrom, String rTo, Map<String, String> params) {
        throw new UnsupportedOperationException("todo");
    }

    public VCSScript<?> ls(String path, Map<String, String> params) {
        throw new UnsupportedOperationException("todo");
    }

    public abstract String head();

    public ConsoleCallback passwordCallback() {
        return SystemEnvironmentPlugin.println($.var(bear.vcsPassword));
    }

    public VCSScript<?> ls(String path) {
        return ls(path, VcsCLIPlugin.emptyParams());
    }


    public <T> T $(DynamicVariable<T> varName) {
        return $.var(varName);
    }

    public  <R extends CommandLineResult> VCSScript<R> newVCSScript() {
        return new VCSScript<R>($.sys, this).cd($.var(bear.vcsBranchLocalPath));
    }

    public  <R extends CommandLineResult> VCSScript<R> newVCSScript(CommandLine<R, VCSScript<R>> line) {
        return this.<R>newVCSScript().add(line);
    }

    public  <R extends CommandLineResult> VCSScript<R> newPlainScript(String command) {
        return this.<R>newVCSScript().line().stty().addRaw(command).build();
    }

    public  <R extends CommandLineResult> VCSScript<R> newPlainScript(String command, Function<String, R> parser) {
        return this.<R>newPlainScript(command)
            .setParser(parser);
    }
}
