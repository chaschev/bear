package bear.vcs;

import org.joda.time.DateTime;

import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public final class LogResult extends CommandLineResult {
    public static class LogEntry{
        public String comment;
        public DateTime date;
        public String revision;
        public String author;

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

    public final List<LogEntry> entries;

    public LogResult(String text, List<LogEntry> entries) {
        super(text);
        this.entries = entries;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("LogResult");
        sb.append(entries);
        return sb.toString();
    }
}
