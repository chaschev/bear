package bear.plugins.sh;

import bear.core.SessionContext;
import bear.session.Variables;
import bear.vcs.CommandLineResult;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.util.Collections;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.transform;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class LsBuilder extends CommandBuilder<LsBuilder> {
    protected String path;
    protected StringBuilder flags = new StringBuilder();

    public static final byte BY_NAME = 0;
    public static final byte BY_DATE = 't';
    public static final byte BY_SIZE = 'S';

    protected byte sortBy = BY_NAME;
    boolean absolutePaths = false;

    public LsBuilder(SessionContext $, String path) {
        super($);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(path));
        this.path = path;
    }

    public LsBuilder byName(){
        sortBy = BY_NAME;
        return this;
    }

    public LsBuilder byDate(){
        sortBy = BY_DATE;
        return this;
    }

    public LsBuilder bySize(){
        sortBy = BY_SIZE;
        return this;
    }

    public LsBuilder absolutePaths(){
        absolutePaths = true;
        return this;
    }

    public static class LsResult extends CommandLineResult {
        List<String> lines;

        public LsResult(String script, String commandOutput, List<String> lines) {
            super(script, commandOutput);
            this.lines = lines;
        }

        public List<String> getLines() {
            return lines;
        }

        @Override
        public LsResult throwIfValidationError() {
            super.throwIfValidationError();
            return this;
        }
    }

    @Override
    public CommandLine<LsResult, Script> asLine() {
        super.asLine();

        CommandLine<LsResult, Script> line = newLine(LsResult.class);

        line.addRaw("ls -w 1 ");

        if (sortBy != BY_NAME) {
            line.addRaw("-" + (char) sortBy);
        }

        line.a(path).setParser(new ResultParser<LsResult>() {
            @Override
            public LsResult parse(String script, String commandOutput) {
                List<String> lines = Variables.LINE_SPLITTER.splitToList(commandOutput);

                if (lines.size() == 1) {
                    if(lines.get(0).isEmpty()){
                        lines = Collections.emptyList();
                    }
                }

                if(absolutePaths){
                    final String parentPath;

                    if(cd.isPresent()){
                        parentPath = $.sys.joinPath(cd.get(), path);
                    }else{
                        parentPath = path;
                    }

                    lines = newArrayList(transform(lines, new Function<String, String>() {
                        public String apply(String s) {
                            return $.sys.joinPath(parentPath + "/" + s);
                        }
                    }));
                }

                return new LsResult(script, commandOutput, lines);
            }
        });

        return line;
    }

    @Override
    public LsResult run() {
        return (LsResult) super.run();
    }
}
