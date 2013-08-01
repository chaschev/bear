package cap4j.session;

import cap4j.CapConstants;
import cap4j.GlobalContext;
import cap4j.scm.BaseScm;
import cap4j.scm.SvnScm;
import cap4j.ssh.MyStreamCopier;
import com.google.common.collect.Lists;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.xfer.scp.SCPFileTransfer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

import static cap4j.CapConstants.sshPassword;
import static cap4j.CapConstants.sshUsername;

/**
 * User: ACHASCHEV
 * Date: 7/23/13
 */
public class GenericUnixRemoteEnvironment extends SystemEnvironment {
    private static final Logger logger = LoggerFactory.getLogger(GenericUnixRemoteEnvironment.class);


    @Override
    public List<String> ls(String path) {
        final BaseScm.CommandLineResult r = run(new BaseScm.CommandLine<BaseScm.CommandLineResult>().a("ls", "-w", "1", path));

        final String[] lines = r.text.split("\n");

        return Lists.newArrayList(lines);
    }

    @Override
    public void zip(String dest, Collection<String> paths) {
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.zip");
    }

    @Override
    public void unzip(String file, @Nullable String destDir) {
        final BaseScm.CommandLine line = new BaseScm.CommandLine<BaseScm.CommandLineResult>()
            .a("unzip");

        if(destDir != null){
            line.a("-d", destDir);
        }else{
            line.a("-d", StringUtils.substringBeforeLast(file, "/")
                );
        }

        line.a("-o", file);

        run(line);
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
    public <T extends SvnScm.CommandLineResult> T run(final BaseScm.CommandLine<T> line) {
        return run(line, null);
    }

    @Override
    public <T extends SvnScm.CommandLineResult> T run(final BaseScm.CommandLine<T> line, @Nullable final SshSession.WithSession inputCallback) {
//        final String[] s = new String[2];
        final Result[] result = {Result.ERROR};
        final SshSession.WithSession withSession = new SshSession.WithSession() {
            @Override
            public void act(final Session session, final Session.Shell shell) throws Exception {
                StringBuilder sb = new StringBuilder(128);

                if (line.cd != null && !".".equals(line.cd)) {
                    sb.append("cd ").append(line.cd).append(" && ");
                }

                if (sudo) {
                    sb.append("stty -echo && sudo ");
                }

                List strings = line.strings;

                for (int i = 0; i < strings.size(); i++) {
                    Object string = strings.get(i);

                    if (string instanceof BaseScm.CommandLineOperator) {
                        sb.append(string);
                    } else {
                        sb.append('"').append(string).append('"');
                    }

                    sb.append(" ");
                }

                final Session.Command exec = session.exec(sb.toString());

                final ByteArrayOutputStream baos = new ByteArrayOutputStream();

                //todo we should also grab error output

                final MyStreamCopier copier = new MyStreamCopier(exec.getInputStream(), baos);

                final int[] myResponseStartsAt = {0};

                final Future<Result> future = copier
                    .bufSize(session.getRemoteMaxPacketSize())
                    .listener(new MyStreamCopier.Listener() {
                        @Override
                        public void reportProgress(long transferred) throws IOException {
                            System.out.printf("transferred: %d%n", transferred);
                            final String text = baos.toString();
                            System.out.println(text);
                            if (inputCallback != null) {
                                inputCallback.text = text;
                                try {
                                    inputCallback.act(session, shell);
                                } catch (Exception e) {
                                    logger.error("", e);
                                }
                            } else {
                                if (text.contains("sudo") && text.contains("password")) {
                                    myResponseStartsAt[0] = text.length();
                                    System.out.println(text);
                                    final String psw = ctx.var(CapConstants.sshPassword) + "\n";
                                    session.getOutputStream().write(
                                        psw.getBytes(IOUtils.UTF8)
                                    );
                                    session.getOutputStream().flush();
                                }
                            }
                        }
                    })
                    .spawn(GlobalContext.INSTANCE.localExecutor);

                exec.join(getTimeout(), TimeUnit.MILLISECONDS);

                copier.stop();
                future.cancel(true);

                final int exitStatus = exec.getExitStatus();

                if(exitStatus == 0){
                    result[0] = Result.OK;
                }
//                try {
//                    result = future.get(getTimeout(), TimeUnit.MILLISECONDS);
//                } catch (Exception e) {
//                    copier.stop();
//                    future.cancel(true);
//                }

                text = baos.toString().substring(myResponseStartsAt[0]).trim();

                logger.info("response: {}", text);

                sudo = false;
            }
        };
        sshSession.withSession(withSession);

//        String text = s[0] + s[1];
//
//        if(text.contains("ouldn") || s[0].contains("rror")){
//            return (T) new SvnScm.CommandLineResult(text, Result.ERROR);
//        }

        return (T) new SvnScm.CommandLineResult(withSession.text, result[0]);

    }

    @Override
    public <T extends SvnScm.CommandLineResult> T runVCS(BaseScm.CommandLine<T> stringResultCommandLine) {
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.runVCS");
    }

    @Override
    public Result sftp(String dest, String host, String path, String user, String pw) {
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.sftp");
    }

    @Override
    public Result scpLocal(String dest, File... files) {
        logger.info("uploading {} files to {}", files.length, dest);

        final SCPFileTransfer transfer = sshSession.getSsh().newSCPFileTransfer();

        try {

            if(files.length == 1){
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
        run(new BaseScm.CommandLine<BaseScm.CommandLineResult>()
            .a("mkdir", "-p")
            .a(dirs)
        );
        return Result.OK;
    }

    @Override
    protected Result copyOperation(String src, String dest, CopyCommandType type, boolean folder, @Nullable String owner) {
        final BaseScm.CommandLine<BaseScm.CommandLineResult> line = new BaseScm.CommandLine<BaseScm.CommandLineResult>();

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

        if(owner == null){
            return run(line).result;
        }else{
            sudo().run(line);
        }

        return sudo().chown(owner, true, dest);
    }

    @Override
    public Result chown(String user, boolean recursive, String... files) {
        final BaseScm.CommandLine<BaseScm.CommandLineResult> line = new BaseScm.CommandLine<BaseScm.CommandLineResult>();

        line.a("chown");
        if(recursive) line.a("-R");
        line.a(user);
        line.a(files);

        final BaseScm.CommandLineResult run = run(line);

        return run.result;
    }

    @Override
    public Result chmod(String octal, boolean recursive, String... files) {
        final BaseScm.CommandLine<BaseScm.CommandLineResult> line = new BaseScm.CommandLine<BaseScm.CommandLineResult>();

        line.a("chmod");
        if(recursive) line.a("-R");
        line.a(octal);
        line.a(files);

        final BaseScm.CommandLineResult run = run(line);

        return run.result;
    }

    @Override
    public Result writeString(String path, String s) {
        try {
            final File tempFile = File.createTempFile("cap4j", "upload");
            FileUtils.writeStringToFile(tempFile, s, IOUtils.UTF8.name());
            scpLocal(path, tempFile);
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
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.exists");
    }

    @Override
    public String readLink(String path) {
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.readLink");
    }

    @Override
    public Result rm(String... paths) {
        return run(new BaseScm.CommandLine().a("rm").a("-r").a(paths)).result;
    }

    public static class MySession{
        Session session;

        public MySession(Session session) {
            this.session = session;
        }
    }

    public static class SshSession {
        private SSHClient ssh;
        private Future<SSHClient> sshFuture;

        SshAddress sshAddress;

        boolean reuseSession = false;
        private Session session;

        public SshSession(final SshAddress sshAddress) {
            this.sshAddress = sshAddress;

            sshFuture = GlobalContext.INSTANCE.localExecutor.submit(new Callable<SSHClient>() {
                @Override
                public SSHClient call() throws Exception {
                    SSHClient ssh = new SSHClient();
                    ssh.loadKnownHosts();
                    ssh.connect(sshAddress.address);
                    ssh.authPassword(sshAddress.username, sshAddress.password);
                    return ssh;
                }
            });
        }

        public synchronized SSHClient getSsh() {
            if(ssh == null){
                try {
                    ssh = sshFuture.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            return ssh;
        }

        public abstract static class WithSession{
            public String text;
            public abstract void act(Session session, Session.Shell shell) throws Exception;
        }

        public void withSession(WithSession withSession) {
            try {
                final Session s = getSession();
//                final Session.Shell shell = s.startShell();
                withSession.act(s, null);
                if(!reuseSession){
                    s.close();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private synchronized Session getSession() throws ConnectionException, TransportException {
            if (reuseSession) {
                if(session == null){
                    session = newSession();
                }
                return session;
            }
            else {
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

    SshSession sshSession;

    public GenericUnixRemoteEnvironment(String name, SshAddress sshAddress) {
        super(name);

        sshSession = new SshSession(sshAddress);
    }

    public static GenericUnixRemoteEnvironment newUnixRemote(String name, String address){
        return newUnixRemote(name, GlobalContext.var(sshUsername), GlobalContext.var(sshPassword), address);
    }
    public static GenericUnixRemoteEnvironment newUnixRemote(String name, String username, String password, String address){
        return new GenericUnixRemoteEnvironment(name, new SshAddress(username, password, address));
    }

}
