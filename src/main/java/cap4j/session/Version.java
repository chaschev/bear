package cap4j.session;

/**
* User: chaschev
* Date: 8/25/13
*/
public class Version {
    public static final Version ANY = new Version("*");
    public static final Version LATEST = new Version("LATEST");

    String version;

    Version(String version) {
        this.version = version;
    }

    public static Version newVersion(String version){
        return new Version(version);
    }

    public boolean matches(Version v){
        return isAny() || version.equals(v.version);
    }

    public boolean isAny(){
        return this == ANY;
    }

    public boolean isLatest(){
        return this == LATEST;
    }

    @Override
    public String toString() {
        return version;
    }

    public static Version fromString(String var){
        if(var == null || var.equals("*")){
            return Version.ANY;
        }

        if("LATEST".equals(var)) return Version.LATEST;

        return Version.newVersion(var);
    }
}
