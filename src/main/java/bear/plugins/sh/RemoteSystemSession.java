package bear.plugins.sh;

import bear.cli.CommandLine;
import bear.cli.Script;
import bear.console.AbstractConsoleCommand;
import bear.console.ConsoleCallback;
import bear.console.ConsoleCallbackResult;
import bear.console.ConsoleCallbackResultType;
import bear.core.AbstractConsole;
import bear.core.GlobalContext;
import bear.core.MarkedBuffer;
import bear.core.SessionContext;
import bear.session.Result;
import bear.session.SshAddress;
import bear.task.Task;
import bear.vcs.CommandLineResult;
import bear.vcs.RemoteCommandLine;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.xfer.scp.SCPFileTransfer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
class RemoteSystemSession extends SystemSession {
    private static final Logger logger = LoggerFactory.getLogger(RemoteSystemSession.class);

    private final GlobalContext global;
    GenericUnixRemoteEnvironmentPlugin.SshSession sshSession;
    private GenericUnixRemoteEnvironmentPlugin remotePlugin;

    public RemoteSystemSession(GenericUnixRemoteEnvironmentPlugin remotePlugin, Task parent, SessionContext $) {
        super(parent, remotePlugin.getTaskDefMixin(), $);
        this.remotePlugin = remotePlugin;
        global = remotePlugin.getGlobal();
        Preconditions.checkNotNull($.address, "address not initialized");
        Preconditions.checkNotNull($.address.getName(), "address not initialized");
        Preconditions.checkNotNull($.address.getAddress(), "address not initialized");

        address = $.address;
    }

    public void checkConnection() {
        if (sshSession == null) {
            sshSession = new GenericUnixRemoteEnvironmentPlugin.SshSession((SshAddress) address, global);
        }
    }

    @Override
    protected  <T extends CommandLineResult> T sendCommandImpl(
        final AbstractConsoleCommand<T> command,
        final ConsoleCallback userCallback) {

        Preconditions.checkArgument(command instanceof CommandLine<?, ?>);

        checkConnection();

        final int[] exitStatus = {0};

        final Result[] result = {Result.ERROR};

        //1. it's also blocking
        //2. add callback
        final GenericUnixRemoteEnvironmentPlugin.SshSession.WithLowLevelSession withSession = new GenericUnixRemoteEnvironmentPlugin.SshSession.WithLowLevelSession(getBear()) {
            @Override
            public void act(final Session session, final Session.Shell shell) throws Exception {

                final Session.Command execSshCommand = session.exec(command.asText());

                final GenericUnixRemoteEnvironmentPlugin.RemoteConsole remoteConsole = (GenericUnixRemoteEnvironmentPlugin.RemoteConsole) new GenericUnixRemoteEnvironmentPlugin.RemoteConsole(execSshCommand, new AbstractConsole.Listener() {
                    @Nonnull
                    @Override
                    public ConsoleCallbackResult textAdded(String textAdded, MarkedBuffer buffer) throws Exception {

                        $.logOutput(textAdded);
                        System.out.print(textAdded);

                        if (Strings.isNullOrEmpty(textAdded)) {
                            return ConsoleCallbackResult.CONTINUE;
                        }

                        final String text = buffer.wholeText();

                        command.append(text);

                        if (userCallback != null) {
                            try {
                                ConsoleCallbackResult progress = userCallback.progress(console, textAdded, buffer.wholeText());
                                switch (progress.type) {
                                    case CONTINUE:
                                        break;
                                    case DONE:
                                        return progress;
                                    case EXCEPTION:
                                        return progress;
                                    case WARNING:
                                        logger.warn("warning during console processing: {}", progress.object);
                                        break;
                                }
                            } catch (Exception e) {
                                logger.error("", e);
                            }
                        } else {
                            if (text.contains("sudo") && text.contains("password")) {
                                buffer.markStart();

                                console.println($.var(bear.sshPassword));
                            }
                        }

                        return new ConsoleCallbackResult(ConsoleCallbackResultType.FINISHED, null);
                    }
                })
                    .bufSize(session.getRemoteMaxPacketSize())
                    .spawn(global.localExecutor, (int) getTimeout(command), TimeUnit.MILLISECONDS);

//                        Stopwatch sw = Stopwatch.createStarted();
                try {
                    execSshCommand.join((int) getTimeout(command), TimeUnit.MILLISECONDS);

//                        logger.debug("join timing: {}", sw.elapsed(TimeUnit.MILLISECONDS));

                    if(!remoteConsole.awaitStreamCopiers(20, TimeUnit.MILLISECONDS)){
    //                            logger.debug("WAAARN, NOT ALL FINISHED!!!");
                        remoteConsole.stopStreamCopiers();
    //                            logger.debug("stopStreamCopiers timing: {}", sw.elapsed(TimeUnit.MILLISECONDS));
                    }
                } finally {
                    if(!remoteConsole.allFinished()){
                        remoteConsole.stopStreamCopiers();
                    }
                }

//                        logger.debug("awaitStreamCopiers timing: {}", sw.elapsed(TimeUnit.MILLISECONDS));

                exitStatus[0] = execSshCommand.getExitStatus();

                if (exitStatus[0] == 0) {
                    result[0] = Result.OK;
                }

                text = remoteConsole.concatOutputs().toString().trim();

                logger.debug("response: {}", text);

                remotePlugin.sudo = false;
            }
        };

        sshSession.withSession(withSession);

        final T t = ((CommandLine<T, ?>)command).parseResult($, withSession.text);

//                System.out.println("WITH_TEXT!!!! '" + withSession.text+"'");

        t.result = Result.and(result[0]);

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
        return line.getTimeoutMs() == 0 ? $(global.bear.defaultTimeout) : line.getTimeoutMs();
    }

    @Override
    public Result sftp(String dest, String host, String path, String user, String pw) {
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.sftp");
    }

    @Override
    public Result upload(String dest, File... files) {
        logger.info("uploading {} files to {}", files.length, dest);

        checkConnection();

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
    public Result scp(String dest, String[] args, String... paths) {
        logger.info("uploading {} files to {}", paths.length, dest);

        checkConnection();

        CommandLine line = script()
            .timeoutMin(60)
            .line()
            .stty();

        line.a("scp");

        if(args!=null){
            line.a(args);
        }

        for (String path : paths) {
            line.a(path);
        }

        line.a(dest);

        CommandLineResult run = line.build().run(SystemEnvironmentPlugin.sshPassword($));

        return run.result;
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
    protected Result copyOperation(String src, String dest, SystemEnvironmentPlugin.CopyCommandType type, boolean folder, @Nullable String owner) {
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
            final File tempFile = File.createTempFile("bear", "upload");
            FileUtils.writeStringToFile(tempFile, s, IOUtils.UTF8.name());
            upload(path, tempFile);
            tempFile.delete();
            return Result.OK;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Result writeStringAs(WriteStringInput input) {
        try {
            final File tempFile = File.createTempFile("bear", "upload");
            FileUtils.writeStringToFile(tempFile, input.text, IOUtils.UTF8.name());
            String remoteTempPath = tempFile.getName();

            upload(remoteTempPath, tempFile);

            tempFile.delete();

            Script script = script();

            CommandLine line = script.line();

            if(input.sudo){
                line.sudo();
            }else{
                line.stty();
            }

            line.a("mv", remoteTempPath, input.path);

            if(input.user.isPresent()){
                line.addRaw(" && chown " + input.user.get() +" ").a(input.path);
            }

            if(input.permissions.isPresent()){
                line.addRaw(" && chmod " + input.permissions.get() +" ").a(input.path);
            }

            CommandLineResult run = run(script, SystemEnvironmentPlugin.println($.var(getBear().sshPassword)));

            return run.result;
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
    public Result download(List<String> paths, SystemEnvironmentPlugin.DownloadMethod method, File destParentDir) {
        logger.info("downloading {} files to {} from {}", paths.size(),
            destParentDir.getAbsolutePath(), remotePlugin.name);

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
}
