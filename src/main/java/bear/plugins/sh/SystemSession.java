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

package bear.plugins.sh;

import bear.cli.CommandLine;
import bear.cli.Script;
import bear.console.AbstractConsole;
import bear.console.AbstractConsoleCommand;
import bear.console.ConsoleCallback;
import bear.core.Role;
import bear.core.SessionContext;
import bear.session.Address;
import bear.session.DynamicVariable;
import bear.session.Result;
import bear.task.BearException;
import bear.task.Task;
import bear.task.TaskResult;
import bear.task.TaskRunner;
import bear.vcs.CommandLineResult;
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
public abstract class SystemSession extends Task<SystemEnvironmentPlugin.SystemSessionDef> implements AbstractConsole {
    private static final Logger logger = LoggerFactory.getLogger(SystemSession.class);
    
    protected Address address;

    protected SystemEnvironmentPlugin.UnixFlavour unixFlavour;

    SystemSession(Task parent, SystemEnvironmentPlugin.SystemSessionDef definition, SessionContext $) {
        super(parent, definition, $);
    }

    protected abstract <T extends CommandLineResult> T sendCommandImpl(AbstractConsoleCommand<T> command, ConsoleCallback userCallback);

    public <T extends CommandLineResult> T sendCommand(AbstractConsoleCommand<T> command){
        return sendCommand(command, null);
    }

    @Override
    public <T extends CommandLineResult> T sendCommand(AbstractConsoleCommand<T> command, ConsoleCallback userCallback) {
        $.getCurrentTask().onCommandExecutionStart(command);

        T result = sendCommandImpl(command, userCallback);

        $.getCurrentTask().onCommandExecutionEnd(command, result);

        return result;
    }

    public CommandLineResult run(Script script) {
        return run(script, null);
    }

    public <T extends CommandLineResult> T run(Script<T, ?> script, ConsoleCallback callback) {
        StringBuilder sb = new StringBuilder(1024);

        for (CommandLine line : script.lines) {
            if (script.cd != null && line.cd != null) {
                line.cd = script.cd;
            }

            final CommandLineResult result = sendCommand(line, callback);

            sb.append(result.text);
        }

        return script.parseResult(sb.toString());
    }

    public <T extends CommandLineResult> T sendCommand(CommandLine<T, ?> commandLine) {
        return sendCommand(commandLine, null);
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

    public CommandLine newCommandLine(Script<?, ?> script) {
        final CommandLine line = newCommandLine(CommandLineResult.class);

        line.setScript(script);

        return line;
    }

    public abstract <T extends CommandLineResult> CommandLine<T, ?> newCommandLine(Class<T> aClass);


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



    public abstract String getAddress();



    public void download(String path) {
        download(Collections.singletonList(path), new File("."));
    }

    public void download(List<String> paths, File destParentDir) {
        download(paths, SystemEnvironmentPlugin.DownloadMethod.SCP, destParentDir);
    }

    public abstract Result download(List<String> paths, SystemEnvironmentPlugin.DownloadMethod method, File destParentDir);





    public abstract Result sftp(String dest, String host, String path, String user, String pw);

    public abstract Result upload(String dest, File... files);

    public abstract Result mkdirs(String... dirs);

    protected abstract Result copyOperation(String src, String dest, SystemEnvironmentPlugin.CopyCommandType type, boolean folder, String owner);

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
        return copyOperation(src, dest, SystemEnvironmentPlugin.CopyCommandType.COPY, false, owner);
    }

    public Result move(String src, String dest) {
        return move(src, dest, null);
    }

    public Result move(String src, String dest, String owner) {
        return copyOperation(src, dest, SystemEnvironmentPlugin.CopyCommandType.MOVE, false, owner);
    }

    public Result link(String src, String dest) {
        return link(src, dest, null);
    }

    public Result link(String src, String dest, @Nullable String owner) {
        return copyOperation(src, dest, SystemEnvironmentPlugin.CopyCommandType.LINK, false, owner);
    }

    public Set<Role> getRoles() {
        return definition.getRoles();
    }

    public String getName() {
        return address.getName();
    }

    public String getDesc() {
        return definition.description;
    }

    public SystemSession sudo() {
        definition.getPlugin().sudo = true;
        return this;
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



    protected SystemEnvironmentPlugin.UnixFlavour computeUnixFlavour() {
        final String text = sendCommand(newCommandLine().a("cat", "/etc/issue")).text;

        if (text == null) return null;

        if (text.contains("CentOS")) return SystemEnvironmentPlugin.UnixFlavour.CENTOS;
        if (text.contains("Ubuntu")) return SystemEnvironmentPlugin.UnixFlavour.UBUNTU;

        return null;
    }

    public SystemEnvironmentPlugin.UnixFlavour getUnixFlavour() {
        if (unixFlavour == null) {
            unixFlavour = computeUnixFlavour();
        }

        return unixFlavour;
    }



    public SystemEnvironmentPlugin.PackageManager getPackageManager() {
        switch (getUnixFlavour()) {
            case CENTOS:
                return new SystemEnvironmentPlugin.PackageManager() {
                    @Override
                    public CommandLineResult installPackage(SystemEnvironmentPlugin.PackageInfo pi) {
                        final CommandLineResult result = sendCommand(newCommandLine().timeoutMin(5).sudo().a("yum", "install", pi.getCompleteName(), "-y"));
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

    public boolean isSudo() {
        return definition.getPlugin().sudo;
    }

    protected TaskResult exec(TaskRunner runner) {
        throw new UnsupportedOperationException("todo .exec");
    }

    public abstract boolean isRemote() ;
}
