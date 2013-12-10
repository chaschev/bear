package bear.plugins.db;

import bear.core.SessionContext;
import bear.main.event.NoticeEventToUI;
import bear.plugins.mongo.MongoDbPlugin;
import bear.vcs.CommandLineResult;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.joda.time.DateTime;

import java.util.List;
import java.util.concurrent.Callable;

import static bear.core.SessionContext.ui;
import static bear.plugins.sh.RmInput.newRm;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class MongoDbService extends DbDumpManager.AbstractDbService<DbDumpManager.MongoDumpableEntry> {
    public MongoDbService(SessionContext $) {
        super($);
    }

    @Override
    public Class<? extends DbDumpInfo> getDbDumpInfoClass() {
        return DbDumpInfo.class;
    }

    @Override
    public Class<DbDumpManager.MongoDumpableEntry> getDumpableEntryClass() {
        return DbDumpManager.MongoDumpableEntry.class;
    }

    @Override
    public DbDumpManager.MongoDumpableEntry list(String dbName) {
        $.plugin(MongoDbPlugin.class).runScript($, null);
        throw new UnsupportedOperationException("todo");
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
                    $.sys.mkdirs($(plugin.dumpFolderPath));

                    ui.info(new NoticeEventToUI("Mongo Restore", "Started mongo restore at " + $($.bear.sessionHostname)
                        + " for dump " + dbDumpInfo.name));

                    $.sys.script().line()
                        .cd($(plugin.sharedDbDumpsPath))
                        .addRaw("tar xvfz %s", $(plugin.dumpArchivePath))
                        .timeoutForInstallation()
                        .build()
                        .run();

                    CommandLineResult result = $.sys.script().line()
                        .timeoutMin(24 * 60) //24h
                        .addRaw("mongorestore %s", $(plugin.dumpFolderPath))
                        .build()
                        .run();

                    ui.info(new NoticeEventToUI("Mongo Restore", "Finished mongo restore at " + $($.bear.sessionHostname)
                        + ", dump " + dbDumpInfo.name));

                    return null;
                }
            }
        );

    }

    @Override
    public DbDumpInfo createDump(DbDumpManager.MongoDumpableEntry entry) {
//            ReleasesPlugin.RELEASE_FORMATTER.
        String dbName = entry.database;

        $.sys.mkdirs($(plugin.dumpFolderPath));

        ui.info(new NoticeEventToUI("Mongo Dump", "Started mongo dump at " + $($.bear.sessionHostname)));

        DateTime startedAt = new DateTime();

        CommandLineResult result = $.sys.script().line().timeoutMin(24 * 60) //24h
            .addRaw("mongodump -d %s -o %s", entry.database, $(plugin.dumpFolderPath)).build().line().timeoutMin(24 * 60)
            .addRaw("tar cvfz %s %s", $(plugin.dumpArchivePath), $(plugin.dumpFolderPath)).build()
            .run();

        $.sys.rm(newRm($(plugin.dumpFolderPath)));

        DateTime finishedAt = new DateTime();

        ui.info(new NoticeEventToUI("Mongo Dump", "Mongo dump finished in " + $($.bear.sessionHostname)));

        List<DbDumpInfo> dumpInfos = Lists.newArrayList(listDumps());

        DbDumpInfo dbDumpInfo = new DbDumpInfo(
            $(plugin.dumpName),
            $(plugin.dbType),
            dbName,
            "",
            startedAt,
            finishedAt,
            -1);

        dumpInfos.add(dbDumpInfo);

        saveDumpList(dumpInfos);

        return dbDumpInfo;
    }

    @Override
    public DbDumpManager.MongoDumpableEntry fromString(String entry) {
        return new DbDumpManager.MongoDumpableEntry(entry);
    }
}
