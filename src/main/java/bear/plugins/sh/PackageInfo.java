package bear.plugins.sh;

import bear.session.Versions;
import com.google.common.base.Strings;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class PackageInfo {
    String name;
    String desc;
    final VersionConstraint versionConstraint;
    final Version version;

    public PackageInfo(String name, VersionConstraint versionConstraint, Version version) {
        this.name = name;
        this.versionConstraint = versionConstraint;
        this.version = version;
    }

    public PackageInfo(String name, VersionConstraint version) {
        this(name, version, null);
    }

    public PackageInfo(String name, Version version) {
        this(name, null, version);
    }

    public PackageInfo(String name) {
        this(name, null, Versions.ANY_VERSION);
    }

    public String getCompleteName() {
        String s;

        if(versionConstraint == null){
            s = Versions.toString(version);
        }else{
            s = Versions.getHighest(versionConstraint);
        }

        s = name + (Strings.isNullOrEmpty(s) ? "" : "-" + s);

        return name + s;
    }

    @Override
    public String toString() {
        return getCompleteName();
    }
}
