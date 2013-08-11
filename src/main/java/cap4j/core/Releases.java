package cap4j.core;

import java.util.Collections;
import java.util.List;

/**
* User: achaschev
* Date: 8/5/13
*/
public class Releases {
    List<String> releases;

    public Releases(List<String> releases) {
        this.releases = releases;
    }

    public String last() {
        return releases.get(releases.size() - 1);
    }

    public String previous() {
        return releases.get(releases.size() - 2);
    }

    public List<String> listToDelete(int keepX) {
        if(releases.size() <= keepX) return Collections.emptyList();

        return releases.subList(0, releases.size() - keepX);
    }
}
