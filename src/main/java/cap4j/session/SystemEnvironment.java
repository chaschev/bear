package cap4j.session;

import cap4j.GlobalContext;
import cap4j.Role;
import cap4j.Variables;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public abstract class SystemEnvironment {
    private boolean unixType = true;

    protected Set<Role> roles = new LinkedHashSet<Role>();

    public Variable joinPath(final Variable... vars) {
        final List<Variable> fromIterable = Arrays.asList(vars);

        return new Variable() {
            public Object apply(Variables.Context input) {
                final Iterable<String> strings = Iterables.transform(fromIterable, new Function<Variable, String>() {
                    public String apply(@Nullable Variable input) {
                        return input.toString();
                    }
                });
                return input.system.joinPath(strings);
            }
        };
    }

    public String diskRoot(){return unixType ? "" : "c:";}

    public String joinPath(Iterable<String> strings) {
        return Joiner.on(dirSeparator()).join(strings);
    }

    public char dirSeparator() {
        return unixType ? '/' : '\\';
    }

    public static abstract class RunResult{
        public Result result;
    }

    public static class CopyResult{

    }

    public static class MakeDirResult{

    }

    public static class GetStringResult{
        Result result;
        String s;
    }

    public enum CopyCommandType{
        COPY, LINK, MOVE
    }

    public abstract RunResult run(String command);
    public abstract Result sftp(String dest, String host, String path, String user, String pw);
    public abstract Result mkdir(String src);
    protected abstract CopyResult copyOperation(String src, String dest, CopyCommandType type, boolean folder);
    public abstract Result chown(String dest, String octal, String user, boolean recursive);
    public abstract Result chmod(String dest, String octal, String user, boolean recursive);
    public abstract Result writeString(String dest, String s);
    public abstract String getString(String dest, String _default);
    public abstract boolean exists(String path);
    public abstract Result uploadRemotelyToMe(File file, String dest);
    public abstract String getName();
    public abstract String readLink(String path);

    public Variable appsDirVar(Variables vars){
        //return vars.
    }


    public CopyResult copy(String src, String dest){
        return copyOperation(src, dest, CopyCommandType.COPY, false);
    }

    public CopyResult move(String src, String dest){
        return copyOperation(src, dest, CopyCommandType.MOVE, false);
    }

    public CopyResult link(String src, String dest){
        return copyOperation(src, dest, CopyCommandType.LINK, false);
    }

    public Set<Role> getRoles() {
        return roles;
    }
}
