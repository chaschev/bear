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

import bear.console.AbstractConsole;
import bear.console.AbstractConsoleCommand;
import bear.console.ConsoleCallback;
import bear.core.Role;
import bear.core.SessionContext;
import bear.core.except.ValidationException;
import bear.session.*;
import bear.task.SessionRunner;
import bear.task.Task;
import bear.task.TaskResult;
import bear.vcs.CommandLineResult;
import chaschev.lang.Predicates2;
import chaschev.util.Exceptions;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static bear.plugins.sh.SystemEnvironmentPlugin.sshPassword;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class SystemSession extends Task<Object, TaskResult> implements AbstractConsole {
    public static final String THROW_ON_ERROR = "THROW_ON_ERROR";
    private static final Logger logger = LoggerFactory.getLogger(SystemSession.class);
    public static final Splitter LINE_SPLITTER = Splitter.on("\n").trimResults();

    protected Address address;

    protected OSInfo osInfo;

    SystemSession(Task parent, SystemEnvironmentPlugin.SystemSessionDef definition, SessionContext $) {
        super(parent, definition, $);
    }

    protected abstract <T extends CommandLineResult> T sendCommandImpl(AbstractConsoleCommand<T> command);

    @Override
    public <T extends CommandLineResult> T sendCommand(AbstractConsoleCommand<T> command) {
        Task<Object, TaskResult> task = $.getCurrentTask();

        task.onCommandExecutionStart(command);

        T result = sendCommandImpl(command);

        task.onCommandExecutionEnd(command, result);

        return result;
    }

    public <T extends CommandLineResult> T run(Script<T, ?> script) {
        StringBuilder sb = new StringBuilder(1024);

        for (CommandLine line : script.lines) {
            if (line.isDefaultDir() && !script.isDefaultDir()) {
                line.cd = script.cd;
            }

            final CommandLineResult result = sendCommand(line);

            sb.append(result.output);

            if(!result.ok()) {
                T r = script.parseResult(sb.toString(), $, script.firstLineAsText());

                return (T) r.copyFrom(result);
            }
        }

        return script.parseResult(sb.toString(), $, script.firstLineAsText());
    }

    public <T extends CommandLineResult> T sendCommand(CommandLine<T, ?> commandLine) {
        return sendCommand((AbstractConsoleCommand<T>) commandLine);
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

    public Script plainScript(String text, boolean sudo) {
        CommandLine line = new Script(this)
            .line();

        if (sudo) {
            line.sudo();
        } else {
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

    protected String capture(String s, boolean sudo) {
        CommandLineResult result = captureResult(s, sudo).throwIfException();

        if (result.nok()) return null;

        return result.output;
    }

    public String capture(String s, ConsoleCallback callback) {
        return captureBuilder(s).sudo(false).callback(callback).run().throwIfError().output;
    }

    public CaptureBuilder captureBuilder(String s) {
        return new CaptureBuilder($, s);
    }

    public CommandLineResult captureResult(String s, boolean sudo) {
        ConsoleCallback callback = sudo ? sshPassword($) : null;

        return captureBuilder(s).sudo(sudo).callback(callback).run();
    }

    public abstract String getAddress();

    public DownloadResult download(String path) {
        return download(Collections.singletonList(path), new File("."));
    }

    public DownloadResult download(List<String> paths, File destParentDir) {
        return download(paths, SystemEnvironmentPlugin.DownloadMethod.SCP, destParentDir);
    }

    public abstract DownloadResult download(List<String> paths, SystemEnvironmentPlugin.DownloadMethod method, File destParentDir);

    public abstract Result sftp(String dest, String host, String path, String user, String pw);

    public abstract Result upload(String dest, File... files);


    public DirsBuilder mkdirs(String... dirs) {
        return DirsBuilder.mk($, dirs);
    }

    public PermissionsCommandBuilder<?> permissions(String... paths) {
        return new PermissionsCommandBuilder<PermissionsCommandBuilder<PermissionsCommandBuilder>>($, paths);
    }

    public abstract WriteStringBuilder writeString(String str);

    public abstract String readString(String path, String _default);

    public abstract boolean exists(String path);

    public abstract String readLink(String path);

    public RmBuilder rm(String... paths) {
        return RmBuilder.newRm($, paths);
    }

    //make it link(dest).toSource(path)
    public LinkOperationBuilder link(String destLinkPath) {
        return CopyOperationBuilder.ln(destLinkPath, $);
    }

    public CopyOperationBuilder copy(String from) {
        return CopyOperationBuilder.cp(from, $);
    }

    public CopyOperationBuilder move(String src) {
        return CopyOperationBuilder.mv(src, $);
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

    public String diskRoot() {
        return isUnix() ? "" : "c:";
    }

    public String joinPath(Object... varsAndObjects) {
        return Joiner.on(dirSeparator())
            .join(Variables.resolveVars($, varsAndObjects))
            .replace("//", "/")
            .replace("/./", "/")
            .replace("//", "/");
    }

    public String joinPath(Iterable<?> varsAndObjects) {
        return joinPath(Iterables.toArray(varsAndObjects, Object.class));
    }

    public char dirSeparator() {
        return isUnix() ? '/' : '\\';
    }

    public List<String> lsQuick(String path) {
        return ls(path).run().throwIfException().lines;
    }

    public LsBuilder ls(String path) {
        return new LsBuilder($, path);
    }

    public void zip(String dest, String... paths) {
        zip(dest, Arrays.asList(paths));
    }

    public abstract void zip(String dest, Collection<String> paths);


    public UnzipCommand unzip(String zipPath) {
        return new UnzipCommand($, zipPath);
    }

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


    public CommandLineResult resetFile(String logPath, boolean sudo) {
        return script().line().sudo(sudo).sshCallback($).addRaw("cat /dev/null >| ").a(logPath).build().run();
    }

    private FileSizeBuilder fileSize(String path) {
        return new FileSizeBuilder($, path);
    }

    public long fileSizeAsLong(String path) {
        return fileSize(path).asLong().run().longValue;
    }

    public String fileSizeAsString(String path) {
        return fileSize(path).asString().run().stringValue;
    }

    //TODO this should be temporary and redesigned
    public static interface OSHelper {
        String upstartCommand(String service, String command);
    }

    public static class OSInfo {
        public final UnixFlavour unixFlavour;
        public final UnixSubFlavour unixSubFlavour;
        public final org.eclipse.aether.version.Version version;
        public final OSHelper osHelper;

        public OSInfo(UnixFlavour unixFlavour, UnixSubFlavour unixSubFlavour, org.eclipse.aether.version.Version version) {
            this.unixFlavour = unixFlavour;
            this.unixSubFlavour = unixSubFlavour;
            this.version = version;

            switch (unixFlavour) {
                case CENTOS:
                    osHelper = new OSHelper() {
                        @Override
                        public String upstartCommand(String service, String command) {
                            return "initctl " + command + " " + service;
                        }
                    };
                    break;
                case UBUNTU:
                    osHelper = new OSHelper() {
                        @Override
                        public String upstartCommand(String service, String command) {
                            return "service " + service + " " + command;
                        }
                    };
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        public OSHelper getHelper() {
            return osHelper;
        }
    }


    protected OSInfo computeUnixFlavour() {

//        versionScheme.parseVersion();

        try {
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

                if (!matcher.find()) {
                    throw new ValidationException("could not parse OS version: " + versionLine);
                }

                version = Versions.VERSION_SCHEME.parseVersion(matcher.group(1));
            } else if (text.contains("Ubuntu")) {
                flavour = UnixFlavour.UBUNTU;
                subFlavour = UnixSubFlavour.UBUNTU;

                Matcher matcher = Pattern.compile("Ubuntu\\s+(\\d+\\.\\d+)").matcher(text);

                if (!matcher.find()) {
                    throw new ValidationException("could not parse OS version: " + text);
                }

                version = Versions.VERSION_SCHEME.parseVersion(matcher.group(1));
            } else {
                throw new UnsupportedOperationException("todo support: " + text);
            }

            return new OSInfo(flavour, subFlavour, version);
        } catch (InvalidVersionSpecificationException e) {
            throw Exceptions.runtime(e);
        }
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
                return new YumPackageManager(this);
            case UBUNTU:
                return new AptPackageManager(this);
            default:
                throw new UnsupportedOperationException("todo support" + getOsInfo() + " !");
        }
    }

    protected TaskResult exec(SessionRunner runner) {
        throw new UnsupportedOperationException("todo .exec");
    }

    public abstract boolean isRemote();

    private static class YumPackageManager extends SystemEnvironmentPlugin.PackageManager {

        private YumPackageManager(SystemSession sys) {
            super(sys);
        }

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
            final CommandLineResult result = sys.sendCommand(
                sys.line().timeoutForInstallation().sudo().a(command(), "install", packageName, "-y"));

            final String text = result.output;

            if (text.contains("Complete!") ||
                text.contains("nothing to do")) {
                result.setResult(Result.OK);
            }

            if (result.getResult().nok()) {
                logger.error("could not install {}", desc);
            }

            return result;
        }

        public String command() {
            return "yum";
        }
    }

    private static class AptPackageManager extends SystemEnvironmentPlugin.PackageManager {
        private AptPackageManager(SystemSession sys) {
            super(sys);
        }

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
            final CommandLineResult result = sys.sendCommand(
                sys.line().timeoutForInstallation().sudo().a(command(), "install", packageName, "-y"));

            final String text = result.output;

            if (text.contains("is already the newest version")) {
                result.setResult(Result.OK);
            }

            if (result.getResult().nok()) {
                logger.error("could not install {}", desc);
            }

            return result;
        }

        public String command() {
            return "apt-get";
        }
    }
}
