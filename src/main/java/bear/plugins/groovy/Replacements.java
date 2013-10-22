package bear.plugins.groovy;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
class Replacements {
    int start;
    int end;

    Replacements(int start, int end) {
        this.start = start;
        this.end = end;
    }

    List<Replacement> replacements = new ArrayList<Replacement>();

    public boolean add(Replacement replacement) {
        return replacements.add(replacement);
    }

    public Replacements addAll(List<GroovyCodeCompleter.Candidate> candidates) {
        replacements.addAll(Lists.transform(candidates,
            new Function<GroovyCodeCompleter.Candidate, Replacement>() {
                public Replacement apply(GroovyCodeCompleter.Candidate input) {
                    return input.r;
                }
            }));

        return this;
    }
}
