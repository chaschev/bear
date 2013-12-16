package bear.plugins.sh;

import bear.core.SessionContext;
import bear.vcs.CommandLineResult;
import com.google.common.base.Optional;

import static com.google.common.base.Optional.of;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class PermissionsCommandInput<SELF extends CommandInput> extends CommandInput<SELF> {
    protected Optional<String> user = Optional.absent();
    protected Optional<String> permissions = Optional.absent();

    public PermissionsCommandInput() {
    }

    public PermissionsCommandInput(SessionContext $) {
        super($);
    }

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
        return addPermissions(line, true, dest);
    }

    public SELF addPermissions(CommandLine<? extends CommandLineResult, ?> line, boolean needsChainAdd, String... paths) {
        if (this.permissions.isPresent()) {
            if(needsChainAdd) line.addRaw(" && ");

            line.addRaw("chmod " + (this.recursive ? "-R " : "") + this.permissions.get() + " ").a(paths);

            needsChainAdd = false;
        }

        if(this.user.isPresent()){
            if(needsChainAdd) line.addRaw(" && ");
            line.addRaw("chown " + (this.recursive ? "-R ":"") + this.user.get() + " ").a(paths);
        }

        return self();
    }

    public boolean hasPermissions() {
        return permissions.isPresent() || user.isPresent();
    }

    public SELF addSudoPermissions(CommandLine<? extends CommandLineResult, ?> line, String... dest) {
        return addPermissions(line.addRaw(" && sudo "), false, dest);
    }
}
