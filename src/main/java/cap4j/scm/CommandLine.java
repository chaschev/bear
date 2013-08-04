package cap4j.scm;

import cap4j.session.Result;
import com.google.common.base.Function;
import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
* User: achaschev
* Date: 8/4/13
*/
public abstract class CommandLine<T extends CommandLineResult>{
    public String cd = ".";

    public List strings = new ArrayList(4);

    public int timeoutMs;

    protected Function<String, T> parser;

    protected CommandLine() {
    }

    public CommandLine a(String... s){
        Collections.addAll(strings, s);
        return this;
    }

    public CommandLine p(Map<String, String> params) {
        for (Map.Entry<String, String> e : params.entrySet()) {
            strings.add(" --" + e.getKey() + "=" + e.getValue() + " ");
        }
        return this;
    }

    public T parseResult(String text){
        if(parser != null){
            final T obj = parser.apply(text);
            obj.text = text;
            return obj;
        }

        return (T) new CommandLineResult(text, Result.OK);
    }

    public CommandLine<T> setParser(Function<String, T> parser) {
        this.parser = parser;
        return this;
    }

    public CommandLine<T> cd(String cd) {
        this.cd = cd;
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("run \"");
        Joiner.on("\" \"").appendTo(sb, strings);
        sb.append("\" in dir '").append(cd).append('\'');
        return sb.toString();
    }

    public CommandLine<T> semicolon() {
        strings.add(new Vcs.CommandLineOperator(";"));
        return this;
    }

    public CommandLine<T> stty() {
        strings.add(new Vcs.CommandLineOperator("stty -echo;"));
        return this;
    }

    public CommandLine<T> sudo() {
        strings.add(new Vcs.CommandLineOperator("stty -echo; sudo "));
        return this;
    }

    public CommandLine<T> timeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;

        return this;
    }

    public CommandLine<T> bash() {
        strings.add(new Vcs.CommandLineOperator("bash -c"));
        return this;
    }

    public CommandLine<T> setVar(String k, String v) {
        strings.add(new Vcs.CommandLineOperator("export " + k + "=" + v +"; "));
        return this;
    }
}
