package bear.plugins.db;

import bear.core.SessionContext;
import bear.main.event.NoticeEventToUI;
import bear.plugins.mysql.MySqlPlugin;
import bear.vcs.CommandLineResult;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.joda.time.DateTime;

import java.util.List;
import java.util.concurrent.Callable;

import static bear.core.SessionContext.ui;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class MysqlDbService extends DbDumpManager.AbstractDbService<DbDumpManager.SqlDumpableEntry> {
    MySqlPlugin mysql;

    public MysqlDbService(SessionContext $) {
        super($);
    }

    @Override
    public Class<? extends DbDumpInfo> getDbDumpInfoClass() {
        return DbDumpInfo.class;
    }

    @Override
    public Class<DbDumpManager.SqlDumpableEntry> getDumpableEntryClass() {
        return DbDumpManager.SqlDumpableEntry.class;
    }

    @Override
    public DbDumpManager.SqlDumpableEntry list(String dbName) {
        throw new UnsupportedOperationException("todo");
    }

    @Override
    public DbDumpInfo createDump(DbDumpManager.SqlDumpableEntry entry) {
//            ReleasesPlugin.RELEASE_FORMATTER.
        String dbName = entry.database;

        ui.info(new NoticeEventToUI("Mysql Dump", "Started mysql dump at " + $($.bear.sessionHostname)));

        DateTime startedAt = new DateTime();

        CommandLineResult result =
            $.sys.script().line().timeoutMin(24 * 60) //24h
                .addRaw("mysqldump --user=%s -p %s | gzip -c >%s",
                    $(mysql.user), entry.database, $(plugin.dumpArchivePath)).build()
                .callback(MySqlPlugin.passwordCallback($(mysql.password)))
                .run();

        DateTime finishedAt = new DateTime();

        ui.info(new NoticeEventToUI("Mysql Dump", "Mysql dump finished in " + $($.bear.sessionHostname)));

        List<DbDumpInfo> dumpInfos = Lists.newArrayList(listDumps());

        DbDumpInfo dbDumpInfo = new DbDumpInfo(
            $(plugin.dumpName),
            $(plugin.dbType),
            dbName,
            "",
            startedAt,
            finishedAt,
            $.sys.fileSizeAsLong($(plugin.dumpArchivePath))
        );

        dumpInfos.add(dbDumpInfo);

        saveDumpList(dumpInfos);

        return dbDumpInfo;
    }

    @Override
    public void restoreDump(final DbDumpInfo dbDumpInfo) {
        $.withMap(
            ImmutableMap.builder()
                .put(plugin.dumpName, dbDumpInfo.name)
                .build(),
            new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    ui.info(new NoticeEventToUI("Mysql Restore", "Started mysql restore at " + $($.bear.sessionHostname)
                        + " for dump " + dbDumpInfo.name));

                    CommandLineResult result =
                        $.sys.script().line().timeoutMin(24 * 60)
                            .addRaw("gunzip -c %s | mysql -u %s -p %s",
                                $(plugin.dumpArchivePath), $(mysql.user), dbDumpInfo.database).build()
                            .callback(MySqlPlugin.passwordCallback($(mysql.password)))
                            .run();

                    ui.info(new NoticeEventToUI("Mongo Restore", "Finished mongo restore at " + $($.bear.sessionHostname)
                        + ", dump " + dbDumpInfo.name));

                    return null;
                }
            }
        );

    }

    @Override
    public DbDumpManager.SqlDumpableEntry fromString(String entry) {
        return new DbDumpManager.SqlDumpableEntry(entry);
    }
}
