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
import bear.session.*;
import bear.task.BearException;
import bear.task.SessionTaskRunner;
import bear.task.Task;
import bear.task.TaskResult;
import bear.vcs.CommandLineResult;
import chaschev.lang.Predicates2;
import chaschev.util.Exceptions;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static bear.plugins.sh.SystemEnvironmentPlugin.sshPassword;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public abstract class SystemSession extends Task<SystemEnvironmentPlugin.SystemSessionDef> implements AbstractConsole {
    private static final Logger logger = LoggerFactory.getLogger(SystemSession.class);
    public static final Splitter LINE_SPLITTER = Splitter.on("\n").trimResults();

    protected Address address;

    protected OSInfo osInfo;

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

//        result.validate($);

        return result;
    }

    public CommandLineResult run(Script script) {
        return run(script, null);
    }

    public <T extends CommandLineResult> T run(Script<T, ?> script, ConsoleCallback callback) {
        StringBuilder sb = new StringBuilder(1024);

        for (CommandLine line : script.lines) {
            if (line.isDefaultDir() && !script.isDefaultDir()) {
                line.cd = script.cd;
            }

            final CommandLineResult result = sendCommand(line, callback);

            sb.append(result.text);
        }

        return script.parseResult(sb.toString(), $, script.firstLineAsText());
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

    public Script plainScript(String text) {
        return plainScript(text, false);
    }

    public Script plainScript(String text, boolean sudo){
        CommandLine line = new Script(this)
            .line();

        if(sudo){
            line.sudo();
        }else{
            line.stty();
        }

        return line.addRaw(text).build();
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
        return capture(s, false);
    }

    public String capture(String s, boolean sudo) {
        return captureResult(s, sudo).throwIfError().text;
    }

    public String capture(String s, ConsoleCallback callback) {
        return captureResult(s, callback).throwIfError().text;
    }

    public CommandLineResult captureResult(String s, ConsoleCallback callback) {
        return captureResult(s, false, callback);
    }

    public CommandLineResult captureResult(String s) {
        return captureResult(s, false);
    }

    public CommandLineResult captureResult(String s, boolean sudo) {
        return captureResult(s, sudo, sudo ? sshPassword($) : null);
    }

    protected CommandLineResult captureResult(String s, boolean sudo, ConsoleCallback callback) {
        CommandLine line = line();

        if(sudo){
            line.sudo();
        }else
        if(callback != null){
            line.stty();
        }

        return sendCommand(line.addRaw(s), callback);
    }

    public CommandLine addRmLine(CommandLine line, String... paths){
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

    public abstract Result writeStringAs(WriteStringInput input);

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
        return getDefinition().getRoles();
    }

    public String getName() {
        return address.getName();
    }

    public String getDesc() {
        return getDefinition().description;
    }

    public SystemSession sudo() {
        getDefinition().getPlugin().sudo = true;
        return this;
    }

    public String diskRoot() {
        return isUnix() ? "" : "c:";
    }

    public String joinPath(Object... varsAndObjects) {
        return Joiner.on(dirSeparator()).join(Variables.resolveVars($, varsAndObjects));
    }

    public String joinPath(Iterable<?> varsAndObjects) {
        return joinPath(Iterables.toArray(varsAndObjects, Object.class));
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

    public Result scp(String dest, String... paths) {
        return scp(dest, null, paths);
    }

    public abstract Result scp(String dest, String[] args, String... paths);

    public Result scpFrom(SessionContext srcSession, String dest, String[] args, String... paths) {
        String prefix = ((SshAddress) srcSession.address).toScpString();

        String[] fullPaths = new String[paths.length];

        for (int i = 0; i < paths.length; i++) {
            String path = paths[i];
            fullPaths[i] = prefix + path;
        }

        return scp(dest, args, fullPaths);
    }

    //TODO this should be temporary and redesigned
    public static interface OSHelper{
        String serviceCommand(String service, String command);
    }

    public static class OSInfo{
        public final UnixFlavour unixFlavour;
        public final UnixSubFlavour unixSubFlavour;
        public final org.eclipse.aether.version.Version version;
        public final OSHelper osHelper;

        public OSInfo(UnixFlavour unixFlavour, UnixSubFlavour unixSubFlavour, org.eclipse.aether.version.Version version) {
            this.unixFlavour = unixFlavour;
            this.unixSubFlavour = unixSubFlavour;
            this.version = version;

            switch (unixFlavour){
                case CENTOS:
                    osHelper = new OSHelper() {
                        @Override
                        public String serviceCommand(String service, String command) {
                            return "initctl " + command + " " + service;
                        }
                    };
                    break;
                default: throw new UnsupportedOperationException();
            }
        }

        public OSHelper getHelper() {
            return osHelper;
        }
    }


    protected OSInfo computeUnixFlavour() {

//        versionScheme.parseVersion();

        final String text = capture("cat /etc/issue");

        if (text == null) return null;

        UnixFlavour flavour = null;
        UnixSubFlavour subFlavour = null;
        org.eclipse.aether.version.Version version = null;

        if (text.contains("CentOS")) {
            flavour = UnixFlavour.CENTOS;
            subFlavour = UnixSubFlavour.CENTOS;

            String versionLine = Iterables.find(LINE_SPLITTER.split(text), Predicates2.contains("CentOS"));
            Matcher matcher = Pattern.compile("release\\s+([^\\s]+)").matcher(versionLine);

            if(!matcher.find()){
                throw new RuntimeException("could not parse OS version: " + versionLine);
            }

            try {
                 version = Versions.VERSION_SCHEME.parseVersion(matcher.group(1));
            } catch (InvalidVersionSpecificationException e) {
                throw Exceptions.runtime(e);
            }
        }else
        if (text.contains("Ubuntu")) {
            throw new UnsupportedOperationException("todo support Ubuntu!");
        }

        return new OSInfo(flavour, subFlavour, version);
    }

    public OSInfo getOsInfo() {
        if (osInfo == null) {
            osInfo = computeUnixFlavour();
        }

        return osInfo;
    }

    public SystemEnvironmentPlugin.PackageManager getPackageManager() {

        //todo move into os info
        switch (getOsInfo().unixFlavour) {
            case CENTOS:
                return new SystemEnvironmentPlugin.PackageManager() {
                    @Override
                    public CommandLineResult installPackage(PackageInfo pi) {
                        String desc = pi.toString();

                        String packageName = pi.getCompleteName();

                        return installPackage(packageName, desc);
                    }

                    public CommandLineResult installPackage(String packageName) {
                        return installPackage(packageName, packageName);
                    }

                    public CommandLineResult installPackage(String packageName, String desc) {
                        final CommandLineResult result = sendCommand(
                            line().timeoutMin(30).sudo().a(command(), "install", packageName, "-y"));

                        final String text = result.text;

                        if (text.contains("Complete!") ||
                            text.contains("nothing to do")) {
                            result.setResult(Result.OK);
                        } else {
                            result.setResult(Result.ERROR);
                        }

                        if (result.getResult().nok()) {
                            logger.error("could not install {}", desc);
                        }

                        return result;
                    }

                    public String command() {
                        return "yum";
                    }
                };
            case UBUNTU:
            default:
                throw new UnsupportedOperationException("todo support" + getOsInfo() + " !");
        }
    }

    public boolean isSudo() {
        return getDefinition().getPlugin().sudo;
    }

    protected TaskResult exec(SessionTaskRunner runner, Object input) {
        throw new UnsupportedOperationException("todo .exec");
    }

    public abstract boolean isRemote() ;
}
