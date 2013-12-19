package bear.plugins.sh;

import bear.core.SessionContext;
import bear.vcs.CommandLineResult;
import org.apache.commons.lang3.StringUtils;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class FileSizeBuilder extends CommandBuilder<FileSizeBuilder> {
    String path;
    boolean humanReadable = true;

    public FileSizeBuilder(SessionContext $, String path) {
        super($);
        this.path = path;
    }

    @Override
    public CommandLine<FileSizeResult, Script> asLine() {
        return newLine(FileSizeResult.class)
            .addRaw("du -s%s '%s'", humanReadable ? "h" : "b", path)
            .setParser(new ResultParser<FileSizeResult>() {
                @Override
                public FileSizeResult parse(String script, String commandOutput) {
                    String s = StringUtils.substringBefore(commandOutput, "\t").trim();

                    long v = humanReadable ? -1 : Long.parseLong(s);

                    return new FileSizeResult(script, commandOutput, s, v);
                }
            });
    }

    public FileSizeBuilder asLong() {
        humanReadable = false;
        return self();
    }

    public FileSizeBuilder asString() {
        humanReadable = true;
        return self();
    }

    public static class FileSizeResult extends CommandLineResult{
        final String stringValue;
        final long longValue;

        public FileSizeResult(String script, String commandOutput, String stringValue, long longValue) {
            super(script, commandOutput);
            this.stringValue = stringValue;
            this.longValue = longValue;
        }

        public String getStringValue() {
            return stringValue;
        }

        public long getLongValue() {
            return longValue;
        }
    }

    @Override
    public FileSizeResult run() {
        return (FileSizeResult) super.run();
    }
}
