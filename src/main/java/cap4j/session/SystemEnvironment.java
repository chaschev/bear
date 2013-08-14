package cap4j.session;

import cap4j.core.*;
import cap4j.scm.CommandLine;
import cap4j.scm.CommandLineResult;
import cap4j.scm.VcsCLI;
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

    private SessionContext ctx;

    public CapConstants cap;

    protected GlobalContext global;

    protected SystemEnvironment(String name, GlobalContext global) {
        this.name = name;
        this.global = global;
    }

    protected SystemEnvironment(String name, String desc, GlobalContext global) {
        this.name = name;
        this.desc = desc;
        this.global = global;
    }

    protected Set<Role> roles = new LinkedHashSet<Role>();

    public static Variables newSessionVars(GlobalContext globalContext, SystemEnvironment environment) {
        return new Variables(environment.getName() + " vars", globalContext.variables);
    }

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

    public boolean isRemote() {
        throw new UnsupportedOperationException("todo");
    }

    public CommandLine newCommandLine(){
        return newCommandLine(CommandLineResult.class);
    }

    public abstract <T extends CommandLineResult> CommandLine<T> newCommandLine(Class<T> aClass);

    public synchronized SessionContext ctx() {
        if(ctx == null){
            ctx = new SessionContext(global, this);
        }
        return ctx;
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

    public CommandLineResult run(VcsCLI.Script script){
        StringBuilder sb = new StringBuilder(1024);
        Result r = Result.OK;

        for (CommandLine line : script.lines) {
            if(script.cd != null){
                line.cd = script.cd;
            }

            final CommandLineResult result = run(line);
            sb.append(result.text);
            sb.append("\n");

            if(result.result != Result.OK){
                r = result.result;
                break;
            }
        }

        return new CommandLineResult(sb.toString(), r);
    }

    public <T extends CommandLineResult> T run(CommandLine<T> commandLine) {
        return run(commandLine, null);
    }

    public abstract <T extends CommandLineResult> T run(CommandLine<T> commandLine, final GenericUnixRemoteEnvironment.SshSession.WithSession inputCallback) ;
    public abstract <T extends CommandLineResult> T runVCS(CommandLine<T> stringResultCommandLine);

    public abstract Result sftp(String dest, String host, String path, String user, String pw);
    public abstract Result scpLocal(String dest, File... files);
    public abstract Result mkdirs(String... dirs);
    protected abstract Result copyOperation(String src, String dest, CopyCommandType type, boolean folder, String owner);
    public abstract Result chown(String user, boolean recursive, String... dest);
    public abstract Result chmod(String octal, boolean recursive, String... files);
    public abstract Result writeString(String path, String s);
    public abstract String readString(String path, String _default);
    public abstract boolean exists(String path);

    public abstract String readLink(String path);

    public abstract Result rmCd(String dir, String... paths);
    public Result rm(String... paths){
        return rmCd(".", paths);
    }

    public Result copy(String src, String dest) {
        return copy(src, dest, null);
    }

    public Result copy(String src, String dest, String owner){
        return copyOperation(src, dest, CopyCommandType.COPY, false, owner);
    }

    public Result move(String src, String dest) {
        return move(src, dest, null);
    }

    public Result move(String src, String dest, String owner){
        return copyOperation(src, dest, CopyCommandType.MOVE, false, owner);
    }

    public Result link(String src, String dest){
        return link(src, dest, null);
    }

    public Result link(String src, String dest, @Nullable String owner){
        return copyOperation(src, dest, CopyCommandType.LINK, false, owner);
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
