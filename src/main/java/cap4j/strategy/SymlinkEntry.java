package cap4j.strategy;

import cap4j.session.DynamicVariable;

/**
* User: chaschev
* Date: 7/31/13
*/
public class SymlinkEntry {
    DynamicVariable<String> sourcePath;
    DynamicVariable<String> destPath;

    public SymlinkEntry(DynamicVariable<String> sourcePath, DynamicVariable<String> destPath) {
        this.sourcePath = sourcePath;
        this.destPath = destPath;
    }
}
