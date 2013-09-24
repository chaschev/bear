package cap4j.session;

import cap4j.cli.*;
import cap4j.cli.Script;
import cap4j.core.*;
import cap4j.scm.CommandLineResult;
import com.google.common.base.Joiner;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.OutputStream;
import java.util.*;

/**
 * User: chaschev
 * Date: 7/21/13
 */
public abstract class SystemEnvironment {
    private static final Logger logger = LoggerFactory.getLogger(SystemEnvironment.class);

    protected boolean sudo;
    String name;
    String desc;
    private int defaultTimeout = 5000;
    private int singleTimeout = -1;

    public SessionContext ctx;

    public CapConstants cap;

    protected GlobalContext global;
    private UnixFlavour unixFlavour;

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

    public static GenericUnixRemoteEnvironment.SshSession.WithSession passwordCallback(final String password) {
        return passwordCallback(null, password);
    }

    public static GenericUnixRemoteEnvironment.SshSession.WithSession passwordCallback(final String text, final String password) {
        return new GenericUnixRemoteEnvironment.SshSession.WithSession(null, text) {
            @Override
            public void act(Session session, Session.Shell shell) throws Exception {
                if(text.contains("password")){
                    final OutputStream os = session.getOutputStream();
                    os.write((password + "\n").getBytes(IOUtils.UTF8));
                    os.flush();
                }
            }
        };
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

    public CommandLine line(){
        return newCommandLine();
    }

    public Script script(){
        return new Script(this);
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

    public void connect(){

    }

    public String capture(String s) {
        return run(line().addRaw(s)).text;
    }

    public static enum DownloadMethod{
        SCP, SFTP
    }

    public void download(String path){
        download(Collections.singletonList(path), new File("."));
    }

    public void download(List<String> paths, File destParentDir){
        download(paths, DownloadMethod.SCP, destParentDir);
    }

    public abstract Result download(List<String> paths, DownloadMethod method, File destParentDir);


    public enum CopyCommandType{
        COPY, LINK, MOVE
    }

    public CommandLineResult run(cap4j.cli.Script script) {
        return run(script, null);
    }

    public CommandLineResult run(Script script, GenericUnixRemoteEnvironment.SshSession.WithSession callback){
        StringBuilder sb = new StringBuilder(1024);
        Result r = Result.OK;

        for (CommandLine line : script.lines) {
            if(script.cd != null){
                line.cd = script.cd;
            }

            final CommandLineResult result = run(line, callback);
            sb.append(result.text);
            sb.append("\n");

//            if(result.result != Result.OK){
//                r = result.result;
//                break;
//            }
        }

        return new CommandLineResult(sb.toString(), r);
    }

    public <T extends CommandLineResult> T run(CommandLine<T> commandLine) {
        return run(commandLine, null);
    }

    public abstract <T extends CommandLineResult> T run(CommandLine<T> commandLine, final GenericUnixRemoteEnvironment.SshSession.WithSession inputCallback) ;

    public abstract Result sftp(String dest, String host, String path, String user, String pw);
    public abstract Result upload(String dest, File... files);
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

    public static enum UnixFlavour {
        CENTOS, UBUNTU
    }

    protected UnixFlavour computeUnixFlavour(){
        final String text = run(newCommandLine().a("cat", "/etc/issue")).text;

        if(text == null) return null;

        if(text.contains("CentOS")) return UnixFlavour.CENTOS;
        if(text.contains("Ubuntu")) return UnixFlavour.UBUNTU;

        return null;
    }

    public UnixFlavour getUnixFlavour(){
        if(unixFlavour == null) {
            unixFlavour = computeUnixFlavour();
        }

        return unixFlavour;
    }

    public static class PackageInfo{
        String name;
        String desc;
        final Version version;

        public PackageInfo(String name, Version version) {
            this.name = name;
            this.version = version;
        }

        public PackageInfo(String name) {
            this.name = name;
            version = Version.ANY;
        }

        public String getCompleteName(){
            return name + ((version == null || version.isAny()) ? "" : "-" + version);
        }

        @Override
        public String toString() {
            return getCompleteName();
        }
    }

    public static abstract class PackageManager{
        public abstract CommandLineResult installPackage(PackageInfo pi);
    }

    public PackageManager getPackageManager(){
        switch (getUnixFlavour()) {
            case CENTOS:
                return new PackageManager() {
                    @Override
                    public CommandLineResult installPackage(PackageInfo pi) {
                        final CommandLineResult result = SystemEnvironment.this.run(newCommandLine().timeoutMin(5).sudo().a("yum", "install", pi.getCompleteName(), "-y"));
                        final String text = result.text;

                        if(text.contains("Complete!") ||
                            text.contains("nothing to do")
                            ) {
                            result.result = Result.OK;
                        }else {
                            result.result = Result.ERROR;
                        }


                        if(result.result.nok()){
                            logger.error("could not install {}", pi);
                        }

                        return result;
                    }
                };
            case UBUNTU:
                default:
                throw new UnsupportedOperationException("todo support" + getUnixFlavour() + " !");
        }
    }
}
