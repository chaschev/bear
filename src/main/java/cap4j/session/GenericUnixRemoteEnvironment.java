package cap4j.session;

import cap4j.scm.BaseScm;
import cap4j.scm.SvnScm;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

/**
 * User: ACHASCHEV
 * Date: 7/23/13
 */
public class GenericUnixRemoteEnvironment extends SystemEnvironment {

    @Override
    public List<String> ls(String path) {
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.ls");
    }

    @Override
    public void zip(String dest, Iterable<String> paths) {
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.zip");
    }

    @Override
    public void unzip(String file, @Nullable String destDir) {
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.unzip");
    }

    @Override
    public String newTempDir() {
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.newTempDir");
    }

    @Override
    public boolean isUnix() {
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.isUnix");
    }

    @Override
    public <T extends SvnScm.CommandLineResult> T run(BaseScm.CommandLine commandLine) {
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.run");
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
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.scpLocal");
    }

    @Override
    public Result mkdirs(String... dirs) {
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.mkdirs");
    }

    @Override
    protected Result copyOperation(String src, String dest, CopyCommandType type, boolean folder) {
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.copyOperation");
    }

    @Override
    public Result chown(String dest, String octal, String user, boolean recursive) {
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.chown");
    }

    @Override
    public Result chmod(String octal, boolean recursive, String... files) {
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.chmod");
    }

    @Override
    public Result writeString(String path, String s) {
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.writeString");
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
        throw new UnsupportedOperationException("todo GenericUnixRemoteEnvironment.rm");
    }

    SshAddress sshAddress;

    public GenericUnixRemoteEnvironment(String name, SshAddress sshAddress) {
        super(name);
        this.sshAddress = sshAddress;
    }

    public GenericUnixRemoteEnvironment setSshAddress(SshAddress sshAddress) {
        this.sshAddress = sshAddress;
        return this;
    }

    public static GenericUnixRemoteEnvironment newUnixRemote(String name, String username, String password, String address){
        return new GenericUnixRemoteEnvironment(name, new SshAddress(username, password, address));
    }


}
