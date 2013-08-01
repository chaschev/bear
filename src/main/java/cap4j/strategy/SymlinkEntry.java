package cap4j.strategy;

import cap4j.session.DynamicVariable;

/**
* User: chaschev
* Date: 7/31/13
*/
public class SymlinkEntry {
    String sourcePath;
    DynamicVariable<String> destPath;
    String owner;

    public SymlinkEntry(String sourcePath, DynamicVariable<String> destPath) {
        this.sourcePath = sourcePath;
        this.destPath = destPath;
    }

    public SymlinkEntry(String sourcePath, DynamicVariable<String> destPath, String owner) {
        this.sourcePath = sourcePath;
        this.destPath = destPath;
        this.owner = owner;
    }
}
