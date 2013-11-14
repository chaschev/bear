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
import bear.console.AbstractConsoleCommand;
import bear.console.ConsoleCallback;
import bear.core.*;
import bear.session.Address;
import bear.session.Result;
import bear.session.SshAddress;
import bear.task.Task;
import bear.vcs.CommandLineResult;
import bear.vcs.RemoteCommandLine;
import chaschev.util.CatchyCallable;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.xfer.scp.SCPFileTransfer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

@Shell("ssh")
public class GenericUnixRemoteEnvironmentPlugin extends SystemEnvironmentPlugin {
    private static final Logger logger = LoggerFactory.getLogger(GenericUnixRemoteEnvironmentPlugin.class);

    public GenericUnixRemoteEnvironmentPlugin(GlobalContext global) {
        super(global, "remote unix plugin");


        this.shell = new ShShellMode(this, cmdAnnotation());
    }

    public static class RemoteConsole extends AbstractConsole {
        Session.Command command;

        public RemoteConsole(Session.Command command, Listener listener) {
            super(listener);
            this.command = command;

            super.addInputStream(command.getInputStream());
            super.addInputStream(command.getErrorStream(), true);
            super.setOut(command.getOutputStream());
        }

    }

    @Override
    public SystemSession newSession(SessionContext $, Task parent) {
        connect();

        return new SystemSession(parent, taskDefMixin, $) {
            SshSession sshSession;

            {
                Preconditions.checkNotNull($.address, "address not initialized");
                Preconditions.checkNotNull($.address.getName(), "address not initialized");
                Preconditions.checkNotNull($.address.getAddress(), "address not initialized");

                address = $.address;
            }

            public void connect() {
                if (sshSession == null) {
                    sshSession = new SshSession((SshAddress) address, global);
                }
            }

            @Override
            protected  <T extends CommandLineResult> T sendCommandImpl(
                final AbstractConsoleCommand<T> command,
                final ConsoleCallback userCallback) {

                Preconditions.checkArgument(command instanceof CommandLine<?, ?>);

                if (sshSession == null) {
                    connect();
                }

                final int[] exitStatus = {0};

                final Result[] result = {Result.ERROR};

                //1. it's also blocking
                //2. add callback
                final SshSession.WithLowLevelSession withSession = new SshSession.WithLowLevelSession(bear) {
                    @Override
                    public void act(final Session session, final Session.Shell shell) throws Exception {

                        final Session.Command exec = session.exec(command.asText());

                        final RemoteConsole remoteConsole = (RemoteConsole) new RemoteConsole(exec, new AbstractConsole.Listener() {
                            @Override
                            public void textAdded(String textAdded, MarkedBuffer buffer) throws Exception {
                                $.logOutput(textAdded);
                                System.out.print(textAdded);

                                if (StringUtils.isBlank(textAdded)) {
                                    return;
                                }

                                final String text = buffer.wholeText();

                                command.append(text);

                                if (userCallback != null) {
                                    try {
                                        userCallback.progress(console, textAdded,
                                            buffer.wholeText());
                                    } catch (Exception e) {
                                        logger.error("", e);
                                    }
                                } else {
                                    if (text.contains("sudo") && text.contains("password")) {
                                        buffer.markStart();

                                        console.println($.var(bear.sshPassword));
                                    }
                                }
                            }
                        })
                            .bufSize(session.getRemoteMaxPacketSize())
                            .spawn(global.localExecutor);

                        exec.join((int) getTimeout(command), TimeUnit.MILLISECONDS);

                        remoteConsole.stopStreamCopiers();
                        remoteConsole.awaitStreamCopiers(5, TimeUnit.MILLISECONDS);

                        exitStatus[0] = exec.getExitStatus();

                        if (exitStatus[0] == 0) {
                            result[0] = Result.OK;
                        }

                        text = remoteConsole.concatOutputs().toString().trim();

                        logger.debug("response: {}", text);

                        sudo = false;
                    }
                };

                sshSession.withSession(withSession);

                final T t = ((CommandLine<T, ?>)command).parseResult(withSession.text);

                t.result = result[0];

                return t;

            }

            @Override
            public List<String> ls(String path) {
                final CommandLineResult r = sendCommand(newCommandLine().a("ls", "-w", "1", path));

                final String[] lines = r.text.split("[\r\n]+");

                if (lines.length == 1 &&
                    (lines[0].contains("ls: cannot access") ||
                        lines[0].contains("such file or directory"))) {
                    return Collections.emptyList();
                }

                return Lists.newArrayList(lines);
            }

            @Override
            public void zip(String dest, Collection<String> paths) {
                throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.zip");
            }

            @Override
            public void unzip(String file, @Nullable String destDir) {
                final CommandLine line = newCommandLine()
                    .a("unzip");

                if (destDir != null) {
                    line.a("-d", destDir);
                } else {
                    line.a("-d", StringUtils.substringBeforeLast(file, "/")
                    );
                }

                line.a("-o", file);

                sendCommand(line);
            }

            @Override
            public String newTempDir() {
                throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.newTempDir");
            }

            @Override
            public boolean isUnix() {
                return true;
            }

            @Override
            public boolean isNativeUnix() {
                return true;
            }

            @Override
            public boolean isRemote() {
                return true;
            }

            @Override
            public <T extends CommandLineResult> CommandLine<T, ?> newCommandLine(Class<T> aClass) {
                return new RemoteCommandLine<T>(this);
            }

            private <T extends CommandLineResult> long getTimeout(AbstractConsoleCommand<T> line) {
                return line.getTimeoutMs() == 0 ? GenericUnixRemoteEnvironmentPlugin.this.getTimeout() : line.getTimeoutMs();
            }

            @Override
            public Result sftp(String dest, String host, String path, String user, String pw) {
                throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.sftp");
            }

            @Override
            public Result upload(String dest, File... files) {
                logger.info("uploading {} files to {}", files.length, dest);

                final SCPFileTransfer transfer = sshSession.getSsh().newSCPFileTransfer();

                try {

                    if (files.length == 1) {
                        transfer.upload(new FileSystemFile(files[0]), dest);
                    } else {
                        for (File file : files) {
                            transfer.upload(new FileSystemFile(file), dest + "/" + file.getName());
                        }
                    }

                    return Result.OK;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public Result mkdirs(final String... dirs) {
                sendCommand(newCommandLine()
                    .a("mkdir", "-p")
                    .a(dirs)
                );
                return Result.OK;
            }

            @Override
            protected Result copyOperation(String src, String dest, CopyCommandType type, boolean folder, @Nullable String owner) {
                final CommandLine line = newCommandLine();

                switch (type) {
                    case COPY:
                        line.a("cp", "-R", src, dest);
                        break;
                    case LINK:
                        line.a("rm", dest);
                        line.semicolon();
                        line.sudo();
                        line.a("ln", "-s", src, dest);
                        break;
                    case MOVE:
                        line.a("mv", src, dest);
                        break;
                }

                if (owner == null) {
                    return sendCommand(line).result;
                } else {
                    sudo().sendCommand(line);
                }

                return sudo().chown(owner, true, dest);
            }

            @Override
            public Result chown(String user, boolean recursive, String... files) {
                final CommandLine<CommandLineResult, ?> line = newCommandLine();

                line.a("chown");
                if (recursive) line.a("-R");
                line.a(user);
                line.a(files);

                final CommandLineResult run = sendCommand(line);

                return run.result;
            }

            @Override
            public Result chmod(String octal, boolean recursive, String... files) {
                final CommandLine<CommandLineResult, ?> line = newCommandLine();

                line.a("chmod");
                if (recursive) line.a("-R");
                line.a(octal);
                line.a(files);

                final CommandLineResult run = sendCommand(line);

                return run.result;
            }

            @Override
            public Result writeString(String path, String s) {
                try {
                    final File tempFile = File.createTempFile("cap4j", "upload");
                    FileUtils.writeStringToFile(tempFile, s, IOUtils.UTF8.name());
                    upload(path, tempFile);
                    tempFile.delete();
                    return Result.OK;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public String readString(String path, String _default) {
                throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.readString");
            }

            @Override
            public boolean exists(String path) {
                final CommandLineResult run = sendCommand(newCommandLine()
                    .a("ls", "-w", "1", path)
                );

                return !(run.text.contains("cannot access") || run.text.contains("o such file"));
            }

            @Override
            public String readLink(String path) {
                throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.readLink");
            }

            @Override
            public Result rmCd(@Nonnull String dir, String... paths) {
                return sendCommand(rmLine(dir, line(), paths)).result;
            }

            @Override
            public CommandLine rmLineImpl(@Nullable String dir, CommandLine line, String... paths) {
                if (dir != null) {
                    line.cd(dir);
                }
                return line.a("rm", "-rf").a(paths);
            }

            @Override
            public String getAddress() {
                return address.getName();
            }

            @Override
            public Result download(List<String> paths, DownloadMethod method, File destParentDir) {
                logger.info("downloading {} files to {} from {}", paths.size(),
                    destParentDir.getAbsolutePath(), name);

                final SCPFileTransfer transfer = sshSession.getSsh().newSCPFileTransfer();

                try {
                    for (String path : paths) {
                        final File destFile = new File(destParentDir, FilenameUtils.getName(path));

                        logger.info("transferring {} to {}", path, destFile.getAbsolutePath());
                        transfer.download(path, new FileSystemFile(destFile));
                    }

                    return Result.OK;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }



    public static class SshSession {
        private SSHClient ssh;
        private Future<SSHClient> sshFuture;

        SshAddress sshAddress;

        boolean reuseSession = false;
        private Session session;

        public SshSession(final SshAddress sshAddress, GlobalContext global) {
            this.sshAddress = sshAddress;

            sshFuture = global.localExecutor.submit(new CatchyCallable<SSHClient>(new Callable<SSHClient>() {
                @Override
                public SSHClient call() throws Exception {
                    try {
                        logger.info("connecting to " + sshAddress.address);
                        SSHClient ssh = new SSHClient();
                        ssh.loadKnownHosts(new File(SystemUtils.getUserHome(), ".ssh/known_hosts"));
                        ssh.connect(sshAddress.address);
                        ssh.authPassword(sshAddress.username, sshAddress.password);
                        return ssh;
                    } catch (Exception e) {
                        final String fingerprint = StringUtils.substringBetween(
                            e.toString(), "fingerprint `", "`");

                        SSHClient ssh = new SSHClient();

                        ssh.loadKnownHosts(new File(SystemUtils.getUserHome(), ".ssh/known_hosts"));
                        ssh.addHostKeyVerifier(fingerprint);
                        ssh.connect(sshAddress.address);
                        ssh.authPassword(sshAddress.username, sshAddress.password);
                        return ssh;
                    }
                }
            }));
        }

        public synchronized SSHClient getSsh() {
            if (ssh == null) {
                try {
                    ssh = sshFuture.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            return ssh;
        }

        private abstract static class WithLowLevelSession {
            public Bear bear;
            public String text;

            protected WithLowLevelSession(Bear bear) {
                this.bear = bear;
            }

            public abstract void act(Session session, Session.Shell shell) throws Exception;
        }

        public void withSession(WithLowLevelSession withSession) {
            try {
                final Session s = getSession();
//                final Session.Shell shell = s.startShell();
                withSession.act(s, null);

                if (!reuseSession) {
                    s.close();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private synchronized Session getSession() throws ConnectionException, TransportException {
            if (reuseSession) {
                if (session == null) {
                    session = newSession();
                }
                return session;
            } else {
                return newSession();
            }
        }

        private Session newSession() throws ConnectionException, TransportException {
            final Session s = getSsh().startSession();
            s.allocateDefaultPTY();
//            try {
//                s.getOutputStream().write("ls\n".getBytes(IOUtils.UTF8));
//                s.getOutputStream().flush();
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
            return s;
        }
    }


    public static Address newUnixRemote(String name, String address) {
        return new SshAddress(name, null, null, address);
    }

    public static SshAddress newUnixRemote(String name, String username, String password, String address) {
        return new SshAddress(name, username, password, address);
    }
}
