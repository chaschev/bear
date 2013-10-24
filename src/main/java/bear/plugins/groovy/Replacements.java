package bear.plugins.groovy;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class Replacements {
    public static final Replacements EMPTY = new Replacements(0, 0);

    int start;
    int end;

    Replacements(int start, int end) {
        this.start = start;
        this.end = end;
    }

    List<Replacement> replacements = new ArrayList<Replacement>();

    boolean add(Replacement replacement) {
        return replacements.add(replacement);
    }

    Replacements addAll(List<GroovyCodeCompleter.Candidate> candidates) {
        replacements.addAll(Lists.transform(candidates,
            new Function<GroovyCodeCompleter.Candidate, Replacement>() {
                public Replacement apply(GroovyCodeCompleter.Candidate input) {
                    return input.r;
                }
            }));

        return this;
    }

    public List<Replacement> getReplacements() {
        return replacements;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }
}
