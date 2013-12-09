package bear.plugins.db;

import bear.context.AbstractContext;
import bear.context.Fun;
import bear.core.GlobalContext;
import bear.core.SessionContext;
import bear.plugins.Plugin;
import bear.plugins.misc.ReleasesPlugin;
import bear.session.DynamicVariable;
import bear.task.*;
import org.joda.time.DateTime;

import static bear.session.Variables.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class DumpManagerPlugin extends Plugin {
    public static enum DbType{
        mongo, mysql
    }

    public final DynamicVariable<String>
        dbType = undefined(),
        sharedDumpsPath = concat(bear.toolsSharedDirPath, "/dumps"),
        sharedDbDumpsPath = concat(sharedDumpsPath, "/", dbType),
        dumpName = dynamic(new Fun<String, AbstractContext>() {
            @Override
            public String apply(AbstractContext $) {
                return ReleasesPlugin.RELEASE_FORMATTER.print(new DateTime()) + ".GMT";
            }
        }).memoizeIn(GlobalContext.class),
        dumpFolderPath = concat(sharedDbDumpsPath, "/", dumpName),
        dumpArchivePath = concat(dumpFolderPath, "/", dumpName, ".tar.gz"),
        dumpsJson = concat(sharedDbDumpsPath, "dumps.json");

    public final DynamicVariable<DbDumpManager.DbService> dbService = dynamic(new Fun<DbDumpManager.DbService, SessionContext>() {
        @Override
        public DbDumpManager.DbService apply(SessionContext $) {
            String s = $.var(dbType);

            switch (DbType.valueOf(s)) {
                case mongo:
                    return new DbDumpManager.MongoDbService($);
                case mysql:
                    throw new UnsupportedOperationException("todo");
                default:
                    throw new UnsupportedOperationException("unsupported: " + s);
            }

        }
    }).memoizeIn(SessionContext.class);

    public DumpManagerPlugin(GlobalContext global) {
        super(global);
    }

    @Override
    public InstallationTaskDef<? extends InstallationTask> getInstall() {
        return new InstallationTaskDef<InstallationTask>() {
            @Override
            protected InstallationTask newSession(SessionContext $, Task parent) {
                return new InstallationTask<InstallationTaskDef>(parent, this, $){
                    @Override
                    protected TaskResult exec(SessionTaskRunner runner, Object input) {
                        return $.sys.mkdirs($(sharedDbDumpsPath)).toTaskResult();
                    }

                    @Override
                    public Dependency asInstalledDependency() {
                        return Dependency.NONE;
                    }
                };
            }
        };
    }
}
