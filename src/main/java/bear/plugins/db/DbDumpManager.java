package bear.plugins.db;

import bear.context.HavingContext;
import bear.core.SessionContext;
import chaschev.util.Exceptions;
import com.bethecoder.table.ASCIITableHeader;
import com.bethecoder.table.impl.SimpleTable;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.bethecoder.table.ASCIITableHeader.h;

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
        private ObjectReader dumpInfoReader = mapper.reader(DB_DUMP_INFO_REF);
        private ObjectWriter dumpInfoWriter = mapper.writerWithDefaultPrettyPrinter();

        public AbstractDbService(SessionContext $) {
            super($);
        }

        @Override
        public DbDumpInfo createDump(String entry) {
            return createDump(fromString(entry));
        }

        public abstract Class<? extends DbDumpInfo> getDbDumpInfoClass();
        public abstract Class<? extends E> getDumpableEntryClass();

        public String printDumpInfo(List<DbDumpInfo> infos){
            String[][] rows = new String[infos.size()][];

            for (int i = 0; i < infos.size(); i++) {
                DbDumpInfo info = infos.get(i);
                rows[i] = new String[]{info.name, info.dbName, info.database, info.comment, info.finishedAt.toString(), info.getSizeAsString()};
            }

            return new SimpleTable().getTable(
                new ASCIITableHeader[]{ h("Name"), h("Type"), h("DB"), h("Comment"), h("Created"), h("Size")},
                rows);
        }

        @Override
        public List<DbDumpInfo> listDumps() {
            String s = $.sys.readString($(plugin.dumpsJson), "");

            if("".equals(s)){
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
                $.sys.writeString($(plugin.dumpsJson), dumpInfoWriter.writeValueAsString(list));
            } catch (JsonProcessingException e) {
                throw Exceptions.runtime(e);
            }
        }

    }

    public static class SqlDumpableEntry implements DumpableEntry{
        String database;

        public SqlDumpableEntry() {
        }

        public SqlDumpableEntry(String database) {
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

}
