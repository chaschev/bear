package bear.plugins.sh;

import com.google.common.base.Optional;

import javax.annotation.Nonnull;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class WriteStringInput {
    public final String path;
    public final String text;
    public final boolean sudo;
    public final Optional<String> user;
    public final Optional<String> permissions;

    public WriteStringInput(@Nonnull String path, @Nonnull String text) {
        this.path = path;
        this.text = text;
        sudo = false;
        user = Optional.absent();
        permissions = Optional.absent();
    }

    public WriteStringInput(@Nonnull String path, @Nonnull String text, boolean sudo, @Nonnull Optional<String> user, @Nonnull Optional<String> permissions) {
        this.path = path;
        this.text = text;
        this.sudo = sudo;
        this.user = user;
        this.permissions = permissions;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("WriteStringInput{");
        sb.append("path='").append(path).append('\'');
        sb.append(", text='").append(text).append('\'');
        if(sudo) sb.append(", sudo");
        if(user.isPresent()) sb.append(", user=").append(user);
        if(permissions.isPresent()) sb.append(", permissions=").append(permissions);
        sb.append('}');
        return sb.toString();
    }
}
