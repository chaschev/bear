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
        dumpName = dynamic(new Fun<AbstractContext, String>() {
            @Override
            public String apply(AbstractContext $) {
                return ReleasesPlugin.RELEASE_FORMATTER.print(new DateTime()) + ".GMT";
            }
        }).memoizeIn(GlobalContext.class),
        dumpFolderPath = concat(sharedDbDumpsPath, "/", dumpName),
        dumpArchivePath = concat(sharedDbDumpsPath, "/", dumpName, ".tar.gz"),
        dumpsJson = concat(sharedDbDumpsPath, "dumps.json");

    public final DynamicVariable<DbDumpManager.DbService> dbService = dynamic(new Fun<SessionContext, DbDumpManager.DbService>() {
        @Override
        public DbDumpManager.DbService apply(SessionContext $) {
            String s = $.var(dbType);

            switch (DbType.valueOf(s)) {
                case mongo:
                    return $.wire(new MongoDbService($));
                case mysql:
                    return $.wire(new MysqlDbService($));
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
        return new InstallationTaskDef<InstallationTask>(new SingleTaskSupplier() {
            @Override
            public Task createNewSession(SessionContext $, Task parent, TaskDef def) {
                return new InstallationTask<InstallationTaskDef>(parent, (InstallationTaskDef) def, $){
                    @Override
                    protected TaskResult exec(SessionRunner runner, Object input) {
                        return $.sys.mkdirs($(sharedDbDumpsPath)).toTaskResult();
                    }

                    @Override
                    public Dependency asInstalledDependency() {
                        return Dependency.NONE;
                    }
                };
            }
        });
    }
}
