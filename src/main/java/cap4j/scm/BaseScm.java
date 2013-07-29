package cap4j.scm;

import cap4j.session.Result;
import com.google.common.base.Function;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: ACHASCHEV
 * Date: 7/24/13
 */
public abstract class BaseScm {
    public SvnScm.CommandLine checkout(String revision, String destination, Map<String, String> params){
        throw new UnsupportedOperationException("todo");
    }

    public SvnScm.CommandLine sync(String revision, String destination, Map<String, String> params){
        throw new UnsupportedOperationException("todo");
    }

    public SvnScm.CommandLine export(String revision, String destination, Map<String, String> params){
        throw new UnsupportedOperationException("todo");
    }

    public SvnScm.CommandLine diff(String rFrom, String rTo, Map<String, String> params){
        throw new UnsupportedOperationException("todo");
    }

    public SvnScm.CommandLine<SvnScm.StringResult> queryRevision(String revision){
        return queryRevision(revision, emptyParams());
    }

    public static Map<String, String> emptyParams() {
        return Collections.emptyMap();
    }

    /**
     * f the given revision represents a "real" revision, this should
     * simply return the revision value. If it represends a pseudo-revision
     * (like Subversions "HEAD" identifier), it should yield a string
     * containing the commands that, when executed will return a string
     * that this method can then extract the real revision from.
     */
    public SvnScm.CommandLine<SvnScm.StringResult> queryRevision(String revision, Map<String, String> params){
        throw new UnsupportedOperationException("todo");
    }

    public String nextRevision(String r){
        return r;
    }

    public abstract String command();

    public SvnScm.CommandLine log(String rFrom, String rTo, Map<String, String> params){
        throw new UnsupportedOperationException("todo");
    }

    public abstract String head();

    public static class CommandLineResult{
        public String text;
        public Result result;

        public CommandLineResult(String text) {
            this.text = text;
        }

        public CommandLineResult(String text, Result result) {
            this.text = text;
            this.result = result;
        }
    }

    public static class StringResult extends CommandLineResult{
        public String value;

        public StringResult(String text, String value) {
            super(text);

            this.value = value;
        }
    }

    public static class Script{
        public String cd = ".";

        public List<CommandLine> lines = new ArrayList<CommandLine>();

        public Script add(CommandLine commandLine) {
            lines.add(commandLine);

            return this;
        }

        public Script cd(String cd) {
            this.cd = cd;
            return this;
        }
    }

    public static class CommandLine<T extends CommandLineResult>{
        public String cd = ".";

        List<String> strings = new ArrayList<String>(4);

        protected Function<String, T> parser;

        public CommandLine a(String... s){
            Collections.addAll(strings, s);
            return this;
        }

        public static CommandLine commandLine(String... s){
            return new CommandLine().a(s);
        }

        public CommandLine p(Map<String, String> params) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                strings.add(" --" + e.getKey() + "=" + e.getValue() + " ");
            }
            return this;
        }

        public T parseResult(String text){
            if(parser != null){return parser.apply(text);}

            return (T) new CommandLineResult(text);
        }

        public CommandLine<T> setParser(Function<String, T> parser) {
            this.parser = parser;
            return this;
        }

        public CommandLine<T> cd(String cd) {
            this.cd = cd;
            return this;
        }
    }
}
