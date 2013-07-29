package cap4j.session;

/**
 * User: ACHASCHEV
 * Date: 7/23/13
 */
public class GenericUnixLocalEnvironment extends SystemEnvironment {

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
    public Result chmod(String octal, boolean recursive, String... files) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public Result writeString(String dest, String s) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public String readString(String dest, String _default) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public boolean exists(String path) {
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

    public GenericUnixLocalEnvironment setLocal(boolean local) {
        return this;
    }
}
