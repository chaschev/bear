package cap4j.strategy;

import cap4j.session.DynamicVariable;

import javax.annotation.Nullable;

/**
* User: chaschev
* Date: 7/31/13
*/
public class SymlinkEntry {
    //path in $current
    String sourcePath;
    //dest path in variable
    DynamicVariable<String> destPath;
    //apply chown if needed
    @Nullable
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
