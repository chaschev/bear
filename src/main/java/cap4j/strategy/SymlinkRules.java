package cap4j.strategy;

import java.util.ArrayList;
import java.util.List;

/**
* User: chaschev
* Date: 7/31/13
*/
public class SymlinkRules {
    List<SymlinkEntry> entries = new ArrayList<SymlinkEntry>(4);

    public SymlinkRules add(SymlinkEntry symlinkEntry) {
        entries.add(symlinkEntry);
        return this;
    }
}
