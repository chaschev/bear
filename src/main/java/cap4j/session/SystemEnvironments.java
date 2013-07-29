package cap4j.session;

import cap4j.GlobalContext;
import com.google.common.base.Predicate;

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
    public Result mkdirs(String... dirs) {
        return getCurrent().mkdirs(dirs);
    }

    @Override
    public Result rm(String... paths) {
        return getCurrent().mkdirs(paths);
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
    public Result chmod(String perms, boolean recursive, String... files) {
        return getCurrent().chmod(perms, recursive, files);
    }

    @Override
    public Result writeString(String dest, String s) {
        return getCurrent().writeString(dest, s);
    }

    @Override
    public String readString(String dest, String _default) {
        return getCurrent().readString(dest, _default);
    }

    @Override
    public boolean exists(String path) {
        return getCurrent().exists(path);
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

    /**
     * Should remove an existing link.
     */
    @Override
    public Result link(String src, String dest) {
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
    public DynamicVariable joinPath(DynamicVariable... vars) {
        return getCurrent().joinPath(vars);
    }

    public boolean add(SystemEnvironment systemEnvironment) {
        return implementations.add(systemEnvironment);
    }

    public List<SystemEnvironment> getImplementations() {
        return implementations;
    }

    public int size() {
        return implementations.size();
    }
}
