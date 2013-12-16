package bear.plugins.sh;

import bear.core.SessionContext;
import bear.vcs.CommandLineResult;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import javax.annotation.Nonnull;

import static com.google.common.base.Optional.of;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class UnzipCommand extends PermissionsCommandInput<UnzipCommand>{

    @Nonnull
    protected final String zipPath;

    @Nonnull
    protected Optional<String> to =Optional.absent();

    public UnzipCommand(SessionContext $, String zipPath) {
        super($);

        this.zipPath = zipPath;
    }

    public UnzipCommand to(String to) {
        this.to = of(to);
        return this;
    }

    public CommandLineResult run(){
        CommandLine line = asLine();

        return $.sys.sendCommand(line);
    }

    @Override
    public CommandLine asLine() {
        boolean hasPermissions = hasPermissions();

        if(hasPermissions){
            Preconditions.checkArgument(to.isPresent(), "destination is required to change permissions");
            sudo();
        }

        CommandLine line = newLine($).addRaw("unzip -q ");

        if(to.isPresent()){
            line.addRaw("-d ").a(to.get()).addRaw(" ");
        }

        if(force){
            line.addRaw("-o ");
        }

        line.a(zipPath);

        if(hasPermissions){
            addSudoPermissions(line, to.get());
        }

        return line;
    }

    @Override
    public void validate() {
        super.validate();
//            Preconditions.checkNotNull(to, "destination must not be null");
    }
}
