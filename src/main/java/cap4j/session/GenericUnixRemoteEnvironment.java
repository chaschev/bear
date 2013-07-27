package cap4j.session;

import java.io.File;

/**
 * User: ACHASCHEV
 * Date: 7/23/13
 */
public class GenericUnixRemoteEnvironment extends SystemEnvironment {
    public static class SshAddress{
        String username;
        String password;
        String address;

        public SshAddress(String username, String password, String address) {
            this.username = username;
            this.password = password;
            this.address = address;
        }
    }

    SshAddress sshAddress;

    @Override
    public RunResult run(String command) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public Result sftp(String dest, String host, String path, String user, String pw) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public Result mkdir(String src) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    protected CopyResult copyOperation(String src, String dest, CopyCommandType type, boolean folder) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public Result chown(String dest, String octal, String user, boolean recursive) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public Result chmod(String dest, String octal, String user, boolean recursive) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public Result writeString(String dest, String s) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public String getString(String dest, String _default) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public boolean exists(String path) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public Result uploadRemotelyToMe(File file, String dest) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public String readLink(String path) {
        throw new UnsupportedOperationException("todo");
    }

    public GenericUnixRemoteEnvironment setSshAddress(SshAddress sshAddress) {
        this.sshAddress = sshAddress;
        return this;
    }

    public static GenericUnixRemoteEnvironment newUnixRemote(String chaschev, String aaaaaa, String s){
        return null;
    }
}
