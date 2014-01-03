package bear.plugins.sh;

import bear.core.SessionContext;
import bear.vcs.CommandLineResult;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class DirsBuilder extends PermissionsCommandBuilder<DirsBuilder> {
    public DirsBuilder(SessionContext $, String... paths) {
        super($, paths);
    }

    public static DirsBuilder mk(SessionContext $, String... dirs){
        return new DirsBuilder($, dirs);
    }

    public static DirsBuilder dirs(SessionContext $, String... dirs){
        return new DirsBuilder($, dirs);
    }

    public static DirsBuilder perm(SessionContext $, String... dirs){
        return new DirsBuilder($, dirs);
    }

    @Override
    public CommandLine<CommandLineResult, Script> asLine() {
        CommandLine line = newLine($).addRaw("mkdir")
            .a("-p");

        line.a(paths);

        if(hasPermissions()){
            addPermissions(line, paths);
        }

        return line;

    }
}
