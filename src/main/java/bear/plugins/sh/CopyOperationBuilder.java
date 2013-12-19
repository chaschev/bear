package bear.plugins.sh;

import bear.core.SessionContext;
import bear.vcs.CommandLineResult;
import com.google.common.base.Preconditions;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class CopyOperationBuilder extends PermissionsCommandBuilder<CopyOperationBuilder> {
    CopyCommandType type;
    String src;
    String dest;

    public CopyOperationBuilder(SessionContext $, CopyCommandType type, String src) {
        super($);
        this.type = type;
        this.src = src;
    }

    public CopyOperationBuilder to(String dest) {
        this.dest = dest;
        return self();
    }

    public static LinkOperationBuilder ln(String dest, SessionContext $){
        return new LinkOperationBuilder($, dest);
    }

    public static CopyOperationBuilder cp(String src, SessionContext $){
        return new CopyOperationBuilder($, CopyCommandType.COPY, src);
    }

    public static CopyOperationBuilder mv(String src, SessionContext $){
        return new CopyOperationBuilder($, CopyCommandType.MOVE, src);
    }

    @Override
    public void validate() {
        super.validate();
        Preconditions.checkNotNull(dest);
    }

    @Override
    public CommandLine asLine() {
        super.asLine();

        CommandLine<? extends CommandLineResult, ?> line = newLine(CommandLineResult.class);

        switch (type) {
            case COPY:
                line.addRaw("cp ")
                    .addRaw(recursive ? "-R " : "")
                    .addRaw(force ? "-f " : "")
                    .a(src, dest);
                break;
            case LINK:
                line.addRaw("rm ").a(dest).addRaw("; ");
                forLine(line, $, false).addRaw("ln -s").a(src, dest);
                break;
            case MOVE:
                line.addRaw("mv ").a(src, dest);
                break;
        }

        if(hasPermissions()){
            addPermissions(line, dest);
        }

        return line;
    }
}
