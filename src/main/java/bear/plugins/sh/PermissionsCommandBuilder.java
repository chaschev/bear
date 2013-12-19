package bear.plugins.sh;

import bear.core.SessionContext;
import bear.core.except.ValidationException;
import bear.vcs.CommandLineResult;
import com.google.common.base.Optional;

import static com.google.common.base.Optional.of;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class PermissionsCommandBuilder<SELF extends PermissionsCommandBuilder> extends CommandBuilder<SELF> {
    protected String[] paths;
    protected Optional<String> user = Optional.absent();
    protected Optional<String> permissions = Optional.absent();

    public PermissionsCommandBuilder(SessionContext $, String... paths) {
        super($);
        this.paths = paths;
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
        if (permissions.isPresent()) {
            if(needsChainAdd) line.addRaw(" && ");

            if(sudo) line.addRaw("sudo ");
            line.addRaw("chmod " + (recursive ? "-R " : "") + permissions.get() + " ").a(paths);

            needsChainAdd = true;
        }

        if(this.user.isPresent()){
            if(needsChainAdd) line.addRaw(" && ");
            if(sudo) line.addRaw("sudo ");
            line.addRaw("chown " + (recursive ? "-R ":"") + user.get() + " ").a(paths);
        }

        return self();
    }

    public boolean hasPermissions() {
        return permissions.isPresent() || user.isPresent();
    }

    @Override
    public CommandLine asLine() {
        super.asLine();

        CommandLine line = newLine($);

        addPermissions(line, false, paths);

        return line;
    }

    public void validateOutput(String script, String output){
        if(output.contains("Operation not permitted")){
            throw new ValidationException(script + ": " + output);
        }
    }
}
