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
public class GenericUnixLocalEnvironment extends SystemEnvironment {
    public GenericUnixLocalEnvironment(String name) {
        super(name);
    }

    @Override
    public List<String> ls(String path) {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.ls");
    }

    @Override
    public void zip(String dest, Iterable<String> paths) {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.zip");
    }

    @Override
    public void unzip(String file, @Nullable String destDir) {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.unzip");
    }

    @Override
    public String newTempDir() {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.newTempDir");
    }

    @Override
    public boolean isUnix() {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.isUnix");
    }

    @Override
    public <T extends SvnScm.CommandLineResult> T run(BaseScm.CommandLine commandLine) {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.run");
    }

    @Override
    public <T extends SvnScm.CommandLineResult> T runVCS(BaseScm.CommandLine<T> stringResultCommandLine) {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.runVCS");
    }

    @Override
    public Result sftp(String dest, String host, String path, String user, String pw) {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.sftp");
    }

    @Override
    public Result scpLocal(String dest, File... files) {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.scpLocal");
    }

    @Override
    public Result mkdirs(String... dirs) {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.mkdirs");
    }

    @Override
    protected Result copyOperation(String src, String dest, CopyCommandType type, boolean folder) {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.copyOperation");
    }

    @Override
    public Result chown(String dest, String octal, String user, boolean recursive) {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.chown");
    }

    @Override
    public Result chmod(String octal, boolean recursive, String... files) {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.chmod");
    }

    @Override
    public Result writeString(String path, String s) {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.writeString");
    }

    @Override
    public String readString(String path, String _default) {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.readString");
    }

    @Override
    public boolean exists(String path) {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.exists");
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.getName");
    }

    @Override
    public String readLink(String path) {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.readLink");
    }

    @Override
    public Result rm(String... paths) {
        throw new UnsupportedOperationException("todo GenericUnixLocalEnvironment.rm");
    }
}
