package bear.plugins.db;

import bear.context.HavingContext;
import bear.core.SessionContext;
import bear.main.event.NoticeEventToUI;
import bear.plugins.mongo.MongoDbPlugin;
import bear.vcs.CommandLineResult;
import chaschev.util.Exceptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.collect.ImmutableMap;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import static bear.core.SessionContext.ui;

/**
 * A simple db dump manager. Dumps are stored under a shared folder on either local machine or on the remote host (default).
 *
 *
 *
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class DbDumpManager {

    private static final TypeReference<List<DbDumpInfo>> DB_DUMP_INFO_REF = new TypeReference<List<DbDumpInfo>>() {
    };

    public static interface DumpableEntry{
        String name();
        String asString();
    }

    public static interface DbService<E extends DumpableEntry>{
        List<DbDumpInfo> listDumps();
        void restoreDump(DbDumpInfo dbDumpInfo);

        E list(String dbName);

        DbDumpInfo createDump(E entry);
        DbDumpInfo createDump(String entry);

        E fromString(String entry);
    }

    public static abstract class AbstractDbService<E extends DumpableEntry> extends HavingContext<AbstractDbService, SessionContext> implements DbService<E>{
        DumpManagerPlugin plugin;

        final ObjectMapper mapper = new ObjectMapper();
        private ObjectReader dumpInfoReader = mapper.reader();

        public AbstractDbService(SessionContext $) {
            super($);
        }

        @Override
        public DbDumpInfo createDump(String entry) {
            return createDump(fromString(entry));
        }

        public abstract Class<? extends DbDumpInfo> getDbDumpInfoClass();
        public abstract Class<? extends E> getDumpableEntryClass();

        @Override
        public List<DbDumpInfo> listDumps() {
            String s = $.sys.readString($(plugin.dumpsJson), null);

            if(s==null){
                return Collections.emptyList();
            }

            try {
                return dumpInfoReader.readValue(s);
            } catch (IOException e) {
                throw Exceptions.runtime(e);
            }
        }

        public void saveDumpList(List<DbDumpInfo> list) {
            try {
                $.sys.writeString($(plugin.dumpsJson), mapper.writeValueAsString(list));
            } catch (JsonProcessingException e) {
                throw Exceptions.runtime(e);
            }
        }

    }

    public static class MongoDumpableEntry implements DumpableEntry{
        String database;

        public MongoDumpableEntry() {
        }

        public MongoDumpableEntry(String database) {
            this.database = database;
        }

        @Override
        public String name() {
            return database;
        }

        @Override
        public String asString() {
            return database;
        }
    }

    public static class MongoDbService extends AbstractDbService<MongoDumpableEntry>{
        MongoDbPlugin mongoPlugin;



        public MongoDbService(SessionContext $) {
            super($);
        }

        @Override
        public Class<? extends DbDumpInfo> getDbDumpInfoClass() {
            return DbDumpInfo.class;
        }

        @Override
        public Class<MongoDumpableEntry> getDumpableEntryClass() {
            return MongoDumpableEntry.class;
        }

        @Override
        public MongoDumpableEntry list(String dbName) {
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
                            .addRaw("mongorestore  ", $(plugin.dumpFolderPath))
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
        public DbDumpInfo createDump(MongoDumpableEntry entry) {
//            ReleasesPlugin.RELEASE_FORMATTER.
            String dbName = entry.database;

            $.sys.mkdirs($(plugin.dumpFolderPath));

            ui.info(new NoticeEventToUI("Mongo Dump", "Started mongo dump at " + $($.bear.sessionHostname)));

            DateTime startedAt = new DateTime();

            CommandLineResult result = $.sys.script().line()
                .timeoutMin(24 * 60) //24h
                .addRaw("mongodump - | tar -cf | gzip > %s ", $(plugin.dumpArchivePath))
                .build()
                .run();

            DateTime finishedAt = new DateTime();

            ui.info(new NoticeEventToUI("Mongo Dump", "Mongo dump finished in " + $($.bear.sessionHostname)));

            List<DbDumpInfo> dumpInfos = listDumps();

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
        public MongoDumpableEntry fromString(String entry) {
            return new MongoDumpableEntry(entry);
        }
    }

    public static class DbDumpInfo{
        String name;
        String dbName;

        // i.e. mongo or mysql
        String database;

        // i.e. name of the collections
        String comment;
        DateTime startedAt;
        DateTime finishedAt;
        long size;

        public DbDumpInfo() {
        }

        public DbDumpInfo(String name, String dbName, String database, String comment, DateTime startedAt, DateTime finishedAt, long size) {
            this.name = name;
            this.dbName = dbName;
            this.database = database;
            this.comment = comment;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
            this.size = size;
        }
    }
}
