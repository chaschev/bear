package bear.plugins.sh;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class DirsInput extends PermissionsCommandInput<DirsInput>{
    String[] dirs;

    public DirsInput(String... dirs) {
        this.dirs = dirs;
    }

    public static DirsInput mk(String... dirs){
        return new DirsInput(dirs);
    }

    public static DirsInput dirs(String... dirs){
        return new DirsInput(dirs);
    }

    public static DirsInput perm(String... dirs){
        return new DirsInput(dirs);
    }
}
