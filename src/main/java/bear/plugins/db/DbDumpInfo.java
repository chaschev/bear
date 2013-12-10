package bear.plugins.db;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.joda.ser.DateTimeSerializer;
import org.joda.time.DateTime;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class DbDumpInfo {
    String name;
    String dbName;

    // i.e. mongo or mysql
    String database;

    // i.e. name of the collections
    String comment;

    @JsonSerialize(using = DateTimeSerializer.class)
    DateTime startedAt;

    @JsonSerialize(using = DateTimeSerializer.class)
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

    public String getName() {
        return name;
    }

    public String getDbName() {
        return dbName;
    }

    public String getDatabase() {
        return database;
    }

    public String getComment() {
        return comment;
    }

    public DateTime getStartedAt() {
        return startedAt;
    }

    public DateTime getFinishedAt() {
        return finishedAt;
    }

    public long getSize() {
        return size;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DbDumpInfo{");
        sb.append("name='").append(name).append('\'');
        sb.append(", dbName='").append(dbName).append('\'');
        sb.append(", database='").append(database).append('\'');
        sb.append(", comment='").append(comment).append('\'');
        sb.append(", startedAt=").append(startedAt);
        sb.append(", finishedAt=").append(finishedAt);
        sb.append(", size=").append(size);
        sb.append('}');
        return sb.toString();
    }
}
