package bear.plugins.groovy;

import java.util.ArrayList;
import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
class Replacements {
    int start;
    int end;

    List<Replacement> replacements = new ArrayList<Replacement>();

    public boolean add(Replacement replacement) {
        return replacements.add(replacement);
    }
}
