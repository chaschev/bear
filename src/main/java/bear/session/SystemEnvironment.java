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

package bear.session;

import bear.cli.CommandLine;
import bear.console.AbstractConsole;
import bear.console.AbstractConsoleCommand;
import bear.console.ConsoleCallback;
import bear.core.*;
import bear.task.BearException;
import bear.task.TaskRunner;
import bear.vcs.CommandLineResult;
import bear.cli.Script;
import com.google.common.base.Joiner;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class SystemEnvironment extends AbstractConsole {
    private static final Logger logger = LoggerFactory.getLogger(SystemEnvironment.class);

    protected boolean sudo;
    String name;
    String desc;
    private int defaultTimeout = 5000;
    private int singleTimeout = -1;

    public Bear bear;

    protected GlobalContext global;
    private UnixFlavour unixFlavour;

    protected SessionContext $;

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

    @Override
    public <T extends CommandLineResult> T sendCommand(AbstractConsoleCommand<T> command, ConsoleCallback userCallback) {
        super.sendCommand(command, userCallback);

        $.getCurrentTask().onCommandExecutionStart(command);

        T result = sendCommandImpl(command, userCallback);

        $.getCurrentTask().onCommandExecutionEnd(command, result);

        return null;
    }

    protected abstract <T extends CommandLineResult> T sendCommandImpl(AbstractConsoleCommand<T> command, ConsoleCallback userCallback);

    public static VariablesLayer newSessionVars(GlobalContext globalContext, SystemEnvironment environment) {
        return new VariablesLayer(environment.getName() + " vars", globalContext.variablesLayer);
    }

    public static ConsoleCallback passwordCallback(final String password) {
        return new ConsoleCallback() {
            @Override
            public void progress(Terminal console, String buffer, String wholeText) {
                if(buffer.contains("password")){
                    console.println(password);
                }
            }
        };
    }

    public DynamicVariable joinPath(final DynamicVariable... vars) {
        final List<DynamicVariable> fromIterable = Arrays.asList(vars);

        return null;
    }

    public String diskRoot() {
        return isUnix() ? "" : "c:";
    }

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

    public void zip(String dest, String... paths) {
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

    public CommandLine line(Script script) {
        return newCommandLine(script);
    }

    public CommandLine line() {
        return newCommandLine();
    }

    public Script script() {
        return new Script(this);
    }

    public CommandLine newCommandLine() {
        return newCommandLine(CommandLineResult.class);
    }

    public CommandLine newCommandLine(Script script) {
        final CommandLine<CommandLineResult> line = newCommandLine(CommandLineResult.class);

        line.setScript(script);

        return line;
    }

    public abstract <T extends CommandLineResult> CommandLine<T> newCommandLine(Class<T> aClass);

    public SessionContext newCtx(TaskRunner runner){
        $ = new SessionContext(global, this, runner);
        runner.set$($);
        return $;
    }

    public SessionContext $() {
        return $;
    }

    public void connect() {

    }

    public String capture(String s) {
        return sendCommand(line().addRaw(s)).text;
    }

    public CommandLine rmLine(CommandLine line, String... paths){
        return rmLine(null, line, paths);
    }

    public CommandLine rmLine(@Nullable String dir, CommandLine line, String... paths){
        for (String path : paths) {
            if(dir != null && !dir.equals(".")){
                path = FilenameUtils.normalize(dir + "/" + path, true);
            }

            path = FilenameUtils.normalize(path, true);

            int dirLevel = StringUtils.split(path, '/').length;

            if(dirLevel <= 2) {
                throw new BearException(String.format("can't delete delete a directory on the second level or higher: %s, dir: %s", dirLevel, path));
            }
        }

        return rmLineImpl(dir, line, paths);
    }

    protected abstract CommandLine rmLineImpl(@Nullable String dir, CommandLine line, String... paths);

    public boolean isSudo() {
        return sudo;
    }

    public static enum DownloadMethod {
        SCP, SFTP
    }

    public void download(String path) {
        download(Collections.singletonList(path), new File("."));
    }

    public void download(List<String> paths, File destParentDir) {
        download(paths, DownloadMethod.SCP, destParentDir);
    }

    public abstract Result download(List<String> paths, DownloadMethod method, File destParentDir);


    public enum CopyCommandType {
        COPY, LINK, MOVE
    }

    public CommandLineResult run(Script script) {
        return run(script, null);
    }

    public <T extends CommandLineResult> T run(Script<T> script, ConsoleCallback callback) {
        StringBuilder sb = new StringBuilder(1024);
        Result r = Result.OK;

        for (CommandLine line : script.lines) {
            if (script.cd != null && line.cd != null) {
                line.cd = script.cd;
            }

            final CommandLineResult result = sendCommand(line, callback);
            sb.append(result.text);
//            sb.append("\n");
//
//            if(result.result != Result.OK){
//                r = result.result;
//                break;
//            }
        }

        return script.parseResult(sb.toString());
    }

    public <T extends CommandLineResult> T sendCommand(CommandLine<T> commandLine) {
        return sendCommand(commandLine, null);
    }

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

    public Result rm(String... paths) {
        return rmCd(".", paths);
    }

    public Result copy(String src, String dest) {
        return copy(src, dest, null);
    }

    public Result copy(String src, String dest, String owner) {
        return copyOperation(src, dest, CopyCommandType.COPY, false, owner);
    }

    public Result move(String src, String dest) {
        return move(src, dest, null);
    }

    public Result move(String src, String dest, String owner) {
        return copyOperation(src, dest, CopyCommandType.MOVE, false, owner);
    }

    public Result link(String src, String dest) {
        return link(src, dest, null);
    }

    public Result link(String src, String dest, @Nullable String owner) {
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

    protected UnixFlavour computeUnixFlavour() {
        final String text = sendCommand(newCommandLine().a("cat", "/etc/issue")).text;

        if (text == null) return null;

        if (text.contains("CentOS")) return UnixFlavour.CENTOS;
        if (text.contains("Ubuntu")) return UnixFlavour.UBUNTU;

        return null;
    }

    public UnixFlavour getUnixFlavour() {
        if (unixFlavour == null) {
            unixFlavour = computeUnixFlavour();
        }

        return unixFlavour;
    }

    public static class PackageInfo {
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

        public String getCompleteName() {
            return name + ((version == null || version.isAny()) ? "" : "-" + version);
        }

        @Override
        public String toString() {
            return getCompleteName();
        }
    }

    public static abstract class PackageManager {
        public abstract CommandLineResult installPackage(PackageInfo pi);
    }

    public PackageManager getPackageManager() {
        switch (getUnixFlavour()) {
            case CENTOS:
                return new PackageManager() {
                    @Override
                    public CommandLineResult installPackage(PackageInfo pi) {
                        final CommandLineResult result = SystemEnvironment.this.sendCommand(newCommandLine().timeoutMin(5).sudo().a("yum", "install", pi.getCompleteName(), "-y"));
                        final String text = result.text;

                        if (text.contains("Complete!") ||
                            text.contains("nothing to do")
                            ) {
                            result.result = Result.OK;
                        } else {
                            result.result = Result.ERROR;
                        }


                        if (result.result.nok()) {
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

    public <T> T $(DynamicVariable<T> varName) {
        return $.var(varName);
    }

    public SystemEnvironment set$(SessionContext $) {
        this.$ = $;
        return this;
    }
}
