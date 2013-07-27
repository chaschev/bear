package cap4j.session;

import cap4j.GlobalContext;
import com.google.common.base.Predicate;

import java.io.File;
import java.util.List;

import static com.google.common.collect.Iterables.find;

/**
 * User: chaschev
 * Date: 7/21/13
 */


//todo don't extends
//todo change to index
public class SystemEnvironments extends SystemEnvironment {
    List<SystemEnvironment> implementations;

    SystemEnvironment current;

    GlobalContext globalContext;

    private SystemEnvironments(SystemEnvironment current) {
        this.current = current;
    }

    @Override
    public RunResult run(String command) {
        return getCurrent().run(command);
    }

    @Override
    public Result sftp(String dest, String host, String path, String user, String pw) {
        return getCurrent().sftp(dest, host, path, user, pw);
    }

    @Override
    public Result mkdir(String src) {
        return getCurrent().mkdir(src);
    }

    @Override
    public CopyResult copyOperation(String src, String dest, CopyCommandType type, boolean folder) {
        return getCurrent().copyOperation(src, dest, type, folder);
    }

    @Override
    public Result chown(String dest, String octal, String user, boolean recursive) {
        return getCurrent().chown(dest, octal, user, recursive);
    }

    @Override
    public Result chmod(String dest, String octal, String user, boolean recursive) {
        return getCurrent().chmod(dest, octal, user, recursive);
    }

    @Override
    public Result writeString(String dest, String s) {
        return getCurrent().writeString(dest, s);
    }

    @Override
    public String getString(String dest, String _default) {
        return getCurrent().getString(dest, _default);
    }

    @Override
    public boolean exists(String path) {
        return getCurrent().exists(path);
    }

    @Override
    public Result uploadRemotelyToMe(File file, String dest) {
        return getCurrent().uploadRemotelyToMe(file, dest);
    }

    @Override
    public String getName() {
        return getCurrent().getName();
    }

    @Override
    public String readLink(String path) {
        return getCurrent().readLink(path);
    }

    @Override
    public CopyResult copy(String src, String dest) {
        return getCurrent().copy(src, dest);
    }

    @Override
    public CopyResult move(String src, String dest) {
        return getCurrent().move(src, dest);
    }

    @Override
    public CopyResult link(String src, String dest) {
        return getCurrent().link(src, dest);
    }



    public static interface EnvRunnable {
        Result run(SystemEnvironment system);
    }

    public Result runForEnvironment(final String name, EnvRunnable runnable) {
        return runnable.run(find(implementations, new Predicate<SystemEnvironment>() {
            @Override
            public boolean apply(SystemEnvironment input) {
                return input.getName().equals(name);
            }
        }));
    }

    private SystemEnvironment getCurrent() {
        return this.current;
    }

    @Override
    public String joinPath(Iterable<String> strings) {
        return getCurrent().joinPath(strings);
    }

    @Override
    public Variable joinPath(Variable... vars) {
        return getCurrent().joinPath(vars);
    }

    public boolean add(SystemEnvironment systemEnvironment) {
        return implementations.add(systemEnvironment);
    }

    public List<SystemEnvironment> getImplementations() {
        return implementations;
    }
}
