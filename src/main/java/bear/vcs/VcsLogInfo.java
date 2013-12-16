package bear.vcs;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.joda.ser.DateTimeSerializer;
import org.joda.time.DateTime;

import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public final class VcsLogInfo extends CommandLineResult {
    public List<LogEntry> entries;

    VcsLogInfo() {
    }

    public VcsLogInfo(String text, List<LogEntry> entries) {
        super("logs", text);
        this.entries = entries;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("LogResult");
        sb.append(entries);
        return sb.toString();
    }

    public String lastComment() {
        LogEntry last = lastEntry();

        if(last == null) return "no log entries";

        return last.comment;
    }

    public String firstComment() {
        LogEntry first = firstEntry();

        if(first == null) return "no log entries";

        return first.comment;
    }

    private LogEntry lastEntry() {
        if(entries.isEmpty()) return null;
        return entries.get(entries.size() - 1);
    }

    private LogEntry firstEntry() {
        if(entries.isEmpty()) return null;
        return entries.get(0);
    }

    public String lastAuthor() {
        LogEntry last = lastEntry();

        if(last == null) return "no log entries";

        return last.author;
    }

    public String firstAuthor() {
        LogEntry first = firstEntry();

        if(first == null) return "no log entries";

        return first.author;
    }

    public static class LogEntry{
        public String comment;
        @JsonSerialize(using = DateTimeSerializer.class)
        public DateTime date;
        public String revision;
        public String author;

        LogEntry() {
        }

        public LogEntry(DateTime date, String author, String comment, String revision) {
            this.date = date;
            this.author = author;
            this.comment = comment;
            this.revision = revision;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("LogEntry{");
            sb.append("comment='").append(comment).append('\'');
            sb.append(", date=").append(date);
            sb.append(", revision='").append(revision).append('\'');
            sb.append(", author='").append(author).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }
}
