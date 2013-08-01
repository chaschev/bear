package cap4j.session;

import cap4j.Role;
import cap4j.VarContext;
import cap4j.scm.BaseScm;
import cap4j.scm.SvnScm;
import com.google.common.base.Joiner;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public abstract class SystemEnvironment {
    protected boolean sudo;
    String name;
    String desc;
    private int defaultTimeout = 5000;
    private int singleTimeout = -1;

    public VarContext ctx;

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
    public abstract void zip(String dest, Collection<String> paths);
    public abstract void unzip(String file, @Nullable String destDir);

    public abstract String newTempDir();

    public abstract boolean isUnix();
    public abstract boolean isNativeUnix();

    protected int getTimeout() {
        int r = singleTimeout == -1 ? defaultTimeout : singleTimeout;
        singleTimeout = -1;
        return r;
    }

    public SystemEnvironment setDefaultTimeout(int defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
        return this;
    }

    public SystemEnvironment setSingleTimeout(int singleTimeout) {
        this.singleTimeout = singleTimeout;
        return this;
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
        COPY, LINK, MOVE;
    }

    public SvnScm.CommandLineResult run(BaseScm.Script script){
        StringBuilder sb = new StringBuilder(1024);
        Result r = Result.OK;

        for (BaseScm.CommandLine line : script.lines) {
            if(script.cd != null){
                line.cd = script.cd;
            }

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

    public <T extends SvnScm.CommandLineResult> T run(BaseScm.CommandLine<T> commandLine) {
        return run(commandLine, null);
    }

    public abstract <T extends SvnScm.CommandLineResult> T run(BaseScm.CommandLine<T> commandLine, final GenericUnixRemoteEnvironment.SshSession.WithSession inputCallback) ;
    public abstract <T extends SvnScm.CommandLineResult> T runVCS(SvnScm.CommandLine<T> stringResultCommandLine);

    public abstract Result sftp(String dest, String host, String path, String user, String pw);
    public abstract Result scpLocal(String dest, File... files);
    public abstract Result mkdirs(String... dirs);
    protected abstract Result copyOperation(String src, String dest, CopyCommandType type, boolean folder);
    public abstract Result chown(String user, boolean recursive, String... dest);
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

    public SystemEnvironment sudo() {
        this.sudo = true;
        return this;
    }
}
