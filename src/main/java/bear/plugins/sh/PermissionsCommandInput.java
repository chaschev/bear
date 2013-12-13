package bear.plugins.sh;

import bear.vcs.CommandLineResult;
import com.google.common.base.Optional;

import static com.google.common.base.Optional.of;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class PermissionsCommandInput<SELF extends CommandInput> extends CommandInput<SELF> {
    protected Optional<String> user = Optional.absent();
    protected Optional<String> permissions = Optional.absent();

    public SELF withPermissions(String permissions){
        this.permissions = of(permissions);
        return self();
    }

    public SELF withPermissions(Optional<String> permissions){
        this.permissions = permissions;
        return self();
    }

    public SELF withUser(Optional<String> user) {
        this.user = user;
        return self();
    }

    public SELF withUser(String user){
        this.user = of(user);
        return self();
    }

    public SELF addPermissions(CommandLine<? extends CommandLineResult, ?> line, String... dest) {
        return addPermissions(line, false, dest);
    }

    public SELF addPermissions(CommandLine<? extends CommandLineResult, ?> line, boolean stillEmpty, String... paths) {
        if (this.permissions.isPresent()) {
            if(!stillEmpty) line.addRaw(" && ");

            line.addRaw("chmod " + (this.recursive ? "-R " : "") + this.permissions.get() + " ").a(paths);

            stillEmpty = false;
        }

        if(this.user.isPresent()){
            if(!stillEmpty) line.addRaw(" && ");
            line.addRaw("chown " + (this.recursive ? "-R ":"") + this.user.get() + " ").a(paths);
        }

        return self();
    }
}
