package cap4j.session;

import cap4j.Role;
import cap4j.scm.BaseScm;
import cap4j.scm.SvnScm;
import com.google.common.base.Joiner;

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

    String name;
    String desc;

    protected SystemEnvironment(String name) {
        this.name = name;
    }

    protected SystemEnvironment(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    protected Set<Role> roles = new LinkedHashSet<Role>();

    public DynamicVariable joinPath(final DynamicVariable... vars) {
        final List<DynamicVariable> fromIterable = Arrays.asList(vars);

        return null;
    }

    public String diskRoot(){return isUnix() ? "" : "c:";}

    public String joinPath(String... strings) {
        return joinPath(Arrays.asList(strings));
    }

    public String joinPath(Iterable<String> strings) {
        return Joiner.on(dirSeparator()).join(strings);
    }

    public char dirSeparator() {
        return isUnix() ? '/' : '\\';
    }


    public abstract List<String> ls(String path);

    public void zip(String dest, String... paths){
        zip(dest, Arrays.asList(paths));
    }
    public abstract void zip(String dest, Iterable<String> paths);
    public abstract void unzip(String file, @Nullable String destDir);

    public abstract String newTempDir();

    public abstract boolean isUnix();


    public static class CopyResult{

    }
    public static class MakeDirResult{

    }

    public static class GetStringResult{

        Result result;
        String s;
    }


    public enum CopyCommandType{
        COPY, LINK, MOVE;
    }

    public SvnScm.CommandLineResult run(BaseScm.Script script){
        StringBuilder sb = new StringBuilder(1024);
        Result r = Result.OK;

        for (BaseScm.CommandLine line : script.lines) {
            final BaseScm.CommandLineResult result = run(line);
            sb.append(result.text);
            sb.append("\n");

            if(result.result != Result.OK){
                r = result.result;
                break;
            }
        }

        return new BaseScm.CommandLineResult(sb.toString(), r);
    }
    public abstract <T extends SvnScm.CommandLineResult> T run(BaseScm.CommandLine commandLine) ;
    public abstract <T extends SvnScm.CommandLineResult> T runVCS(SvnScm.CommandLine<T> stringResultCommandLine);

    public abstract Result sftp(String dest, String host, String path, String user, String pw);
    public abstract Result scpLocal(String dest, File... files);
    public abstract Result mkdirs(String... dirs);
    protected abstract Result copyOperation(String src, String dest, CopyCommandType type, boolean folder);
    public abstract Result chown(String dest, String octal, String user, boolean recursive);
    public abstract Result chmod(String octal, boolean recursive, String... files);
    public abstract Result writeString(String path, String s);
    public abstract String readString(String path, String _default);
    public abstract boolean exists(String path);

    public abstract String readLink(String path);
    public abstract Result rm(String... paths);

    public Result copy(String src, String dest){
        return copyOperation(src, dest, CopyCommandType.COPY, false);
    }

    public Result move(String src, String dest){
        return copyOperation(src, dest, CopyCommandType.MOVE, false);
    }

    public Result link(String src, String dest){
        return copyOperation(src, dest, CopyCommandType.LINK, false);
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }
}
