package bear.plugins.sh;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import javax.annotation.Nonnull;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class WriteStringInput extends PermissionsCommandInput<WriteStringInput>{
    protected String path;
    protected String text;
    protected boolean ifDiffers;

    public WriteStringInput(@Nonnull String path, @Nonnull String text) {
        this.path = path;
        this.text = text;
        sudo = false;
        user = Optional.absent();
    }

    public WriteStringInput ifDiffers() {
        this.ifDiffers = true;
        return this;
    }

    public WriteStringInput(@Nonnull String path, @Nonnull String text, boolean sudo, @Nonnull Optional<String> user, @Nonnull Optional<String> permissions) {
        this.path = path;
        this.text = text;
        this.sudo = sudo;
        this.user = user;
        this.permissions = permissions;
    }

    public static WriteStringInput str(String path, String text){
        Preconditions.checkNotNull(path);
        Preconditions.checkNotNull(text);

        return new WriteStringInput(path, text);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("WriteStringInput{");
        sb.append("path='").append(path).append('\'');
        sb.append(", text='").append(text).append('\'');
        if(ifDiffers) sb.append(", if differs");
        if(sudo) sb.append(", sudo");
        if(user.isPresent()) sb.append(", user=").append(user);
        if(permissions.isPresent()) sb.append(", permissions=").append(permissions);
        sb.append('}');
        return sb.toString();
    }



    public String getFullPath(){
        return cd.isPresent() ? cd.get() + "/" + path : path;
    }

    @Override
    public void validateBeforeSend() {
        super.validateBeforeSend();

        Preconditions.checkNotNull(path);
        Preconditions.checkNotNull(text);
    }
}
