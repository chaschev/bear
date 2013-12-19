package bear.plugins.sh;

import bear.core.SessionContext;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import javax.annotation.Nonnull;

import static com.google.common.base.Optional.of;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class UnzipCommand extends PermissionsCommandBuilder<UnzipCommand> {

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
            addPermissions(line, to.get());
        }

        return line;
    }

    @Override
    public void validate() {
        super.validate();
    }
}
