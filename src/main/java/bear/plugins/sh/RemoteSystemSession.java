package bear.plugins.sh;

import bear.console.AbstractConsoleCommand;
import bear.console.ConsoleCallback;
import bear.console.ConsoleCallbackResult;
import bear.core.AbstractConsole;
import bear.core.GlobalContext;
import bear.core.MarkedBuffer;
import bear.core.SessionContext;
import bear.session.Result;
import bear.session.SshAddress;
import bear.task.BearException;
import bear.task.Task;
import bear.task.TaskResult;
import bear.vcs.CommandLineResult;
import bear.vcs.RemoteCommandLine;
import chaschev.util.Exceptions;
import com.google.common.base.*;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.xfer.scp.SCPFileTransfer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class RemoteSystemSession extends SystemSession {

    private static final Logger logger = LoggerFactory.getLogger(RemoteSystemSession.class);

    private final GlobalContext global;

    private final boolean printToConsole;

    private GenericUnixRemoteEnvironmentPlugin remotePlugin;

    GenericUnixRemoteEnvironmentPlugin.SshSession sshSession;

    public RemoteSystemSession(GenericUnixRemoteEnvironmentPlugin remotePlugin, Task parent, SessionContext $) {
        super(parent, remotePlugin.getTaskDefMixin(), $);
        this.remotePlugin = remotePlugin;
        global = remotePlugin.getGlobal();
        Preconditions.checkNotNull($.address, "address not initialized");
        Preconditions.checkNotNull($.address.getName(), "address not initialized");
        Preconditions.checkNotNull($.address.getAddress(), "address not initialized");

        address = $.address;

        printToConsole = global.var(global.bear.printHostsToConsole);
    }

    public void checkConnection() {
        if (sshSession == null) {
            sshSession = new GenericUnixRemoteEnvironmentPlugin.SshSession((SshAddress) address, global);
        }
    }

    @Override
    protected  <T extends CommandLineResult> T sendCommandImpl(
        final AbstractConsoleCommand<T> command) {

        final ConsoleCallback userCallback = command.getCallback();

        Preconditions.checkArgument(command instanceof CommandLine<?, ?>);

        checkConnection();

        final int[] exitStatus = {0};

        final TaskResult[] result = {new TaskResult(Result.ERROR)};

        //1. it's also blocking
        //2. add callback
        final GenericUnixRemoteEnvironmentPlugin.SshSession.WithLowLevelSession withSession = new GenericUnixRemoteEnvironmentPlugin.SshSession.WithLowLevelSession(getBear()) {
            @Override
            public void act(final Session session, final Session.Shell shell) throws Exception {
                final Session.Command execSshCommand = session.exec(command.asText());

                final GenericUnixRemoteEnvironmentPlugin.RemoteConsole remoteConsole = (GenericUnixRemoteEnvironmentPlugin.RemoteConsole) new GenericUnixRemoteEnvironmentPlugin.RemoteConsole(session, execSshCommand, new AbstractConsole.Listener() {
                    @Nonnull
                    @Override
                    public ConsoleCallbackResult textAdded(String textAdded, MarkedBuffer buffer) throws Exception {
                        $.logOutput(textAdded);

                        if(printToConsole){
                            System.out.print(textAdded);
                        }else{
                            if(!StringUtils.isBlank(textAdded)){
                                logger.debug("text: {}", textAdded);
                            }
                        }

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

                        return ConsoleCallbackResult.CONTINUE;
                    }
                })
                    .bufSize(session.getRemoteMaxPacketSize())
                    .spawn(global.localExecutor, (int) getTimeout(command), TimeUnit.MILLISECONDS);

                Stopwatch sw = Stopwatch.createStarted();

                boolean wasInterrupted = false;
                Exception error = null;

                try {
                    execSshCommand.join((int) getTimeout(command), TimeUnit.MILLISECONDS);

                    if(!remoteConsole.awaitStreamCopiers(20, TimeUnit.MILLISECONDS)){
    //                            logger.debug("WAAARN, NOT ALL FINISHED!!!");
                        remoteConsole.stopStreamCopiers();
    //                            logger.debug("stopStreamCopiers timing: {}", sw.elapsed(TimeUnit.MILLISECONDS));
                    }

                    if(logger.isDebugEnabled()){
                        logger.debug("join timing: {} ms, cmd: {}", sw.elapsed(TimeUnit.MILLISECONDS), command.asText(false));
                    }
                } catch (ConnectionException e){
                    if(logger.isDebugEnabled()){
                        logger.debug("(exception) join timing: {} ms, cmd: {}, ex: {}", sw.elapsed(TimeUnit.MILLISECONDS), command.asText(false),
                            Throwables.getStackTraceAsString(e));
                    }
                    // can be interrupted by self:
                    // callback returns done|exception
                    //
                    error = e;
                    if(Throwables.getRootCause(e) instanceof InterruptedException){
                        wasInterrupted = true;
                    }
                }
                finally {
                    if(!remoteConsole.allFinished()){
                        remoteConsole.stopStreamCopiers();
                    }
                }

//                        logger.debug("awaitStreamCopiers timing: {}", sw.elapsed(TimeUnit.MILLISECONDS));

                Integer code = execSshCommand.getExitStatus();

                if(code == null){
                    logger.info("command has been preliminarily stopped (null code for {})", command.asText(false));
                }

                // it returns null when connection is closed on our side
                // connection can be closed in a callback by returning DONE
                exitStatus[0] = code == null ? 0 : code;

                if (exitStatus[0] == 0) {
                    result[0].setResult(Result.OK);
                }

                if(error != null){
                    result[0].setException(error);
                }

                Optional<ConsoleCallbackResult> lastCallbackError = remoteConsole.getLastError();

                if(lastCallbackError.isPresent()){
                    result[0].setException(new Exception("last callback error: " + lastCallbackError.get().object));
                }

//                logger.debug("OOOOOOOOOOOOPS, error in sendImpl: {}", lastCallbackError);

                text = remoteConsole.concatOutputs().toString().trim();

                logger.debug("response: {}", text);
            }
        };

        sshSession.withSession(withSession);

        final T t = ((CommandLine<T, ?>)command).parseResult($, withSession.text);

//                System.out.println("WITH_TEXT!!!! '" + withSession.text+"'");


        t.setResult(Result.and(t.getResult(), result[0].getResult()));

        if(result[0].exception.isPresent()){
            t.setException(result[0].exception.get());
        }

        return t;

    }

    @Override
    public void zip(String dest, Collection<String> paths) {
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.zip");
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
                logger.info("transferring {} to {}", files[0], dest);

                transfer.upload(new FileSystemFile(files[0]), dest);
            } else {
                for (File file : files) {
                    logger.info("transferring {} to {}", file, dest);

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

        if(line.getCallback() == null){
            line.setCallback(SystemEnvironmentPlugin.sshPassword($));
        }

        CommandLineResult run = line.build().run();

        return run.getResult();
    }

    @Override
    public WriteStringBuilder writeString(String str) {
        return new WriteStringBuilder($, str);
    }

    @Override
    public String readString(String path, String _default) {
        DownloadResult downloadResult = download(Collections.singletonList(path), new File(global.localCtx.var(getBear().tempDirPath)));

        if(!downloadResult.ok()){
            if(THROW_ON_ERROR.equals(_default)){
                throw downloadResult.exception.isPresent() ?
                    new BearException("unable to download: " + path, downloadResult.exception.get()) :
                    new BearException("unable to download: " + path);
            }else{
                return _default;
            }
        }

        try {
            return FileUtils.readFileToString(downloadResult.files.get(0));
        } catch (IOException e) {
            throw Exceptions.runtime(e);
        }
    }

    @Override
    public boolean exists(String path) {
        final CommandLineResult run = sendCommand(newCommandLine()
            .a("ls", "-w", "1", path)
        );

        return !(run.output.contains("cannot access") || run.output.contains("o such file"));
    }

    @Override
    public String readLink(String path) {
        CommandLineResult r = script().line().a("readlink", path).build().run();

        if(!r.ok()) return null;

        String result = r.output.trim();

        return Strings.emptyToNull(result);
    }

    @Override
    public String getAddress() {
        return address.getName();
    }

    @Override
    public DownloadResult download(List<String> paths, SystemEnvironmentPlugin.DownloadMethod method, File destParentDir) {
        logger.info("downloading {} files to {} from {}", paths.size(),
            destParentDir.getAbsolutePath(), remotePlugin.name);

        if(!destParentDir.exists()){
            if(!destParentDir.mkdirs()){
                throw new BearException("unable to create dir: " + destParentDir);
            }
        }

        checkConnection();

        try {
            SFTPClient sftpClient = sshSession.getSsh().newSFTPClient();
            List<File> files = new ArrayList<File>(paths.size());

            for (String path : paths) {
                final File destFile = new File(destParentDir, FilenameUtils.getName(path));

                logger.info("transferring {} to {}", path, destFile.getAbsolutePath());

                sftpClient.get(path, new FileSystemFile(destFile));

                files.add(destFile);
            }

            return new DownloadResult(files);
        } catch (IOException e) {
            return new DownloadResult(e);
        }

//        final SCPFileTransfer transfer = sshSession.getSsh().newSCPFileTransfer();
//
//        try {
//            List<File> files = new ArrayList<File>(paths.size());
//
//            for (String path : paths) {
//                final File destFile = new File(destParentDir, FilenameUtils.getName(path));
//
//                logger.info("transferring {} to {}", path, destFile.getAbsolutePath());
//                transfer.download(path, new FileSystemFile(destFile));
//
//                files.add(destFile);
//            }
//
//            return new DownloadResult(files);
//        } catch (IOException e) {
//            return new DownloadResult(e);
//        }
    }
}
