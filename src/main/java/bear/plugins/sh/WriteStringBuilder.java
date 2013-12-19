package bear.plugins.sh;

import bear.core.SessionContext;
import bear.vcs.CommandLineResult;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import net.schmizz.sshj.common.IOUtils;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class WriteStringBuilder extends PermissionsCommandBuilder<WriteStringBuilder> {
    protected String path;
    protected String text;
    protected boolean ifDiffers;

    public WriteStringBuilder(SessionContext $, @Nonnull String text) {
        super($);
        this.text = text;
        sudo = false;
        user = Optional.absent();
    }

    public WriteStringBuilder ifDiffers() {
        this.ifDiffers = true;
        return this;
    }

    public WriteStringBuilder(SessionContext $, @Nonnull String text, boolean sudo, @Nonnull Optional<String> user, @Nonnull Optional<String> permissions) {
        super($);
        this.text = text;
        this.sudo = sudo;
        this.user = user;
        this.permissions = permissions;
    }

    public static WriteStringBuilder str(SessionContext $, String path, String text){
        Preconditions.checkNotNull(text);

        return new WriteStringBuilder($, text);
    }

    public WriteStringBuilder toPath(String path){
        Preconditions.checkNotNull(path);
        this.path = path;
        return this;
    }

    @Override
    public CommandLine asLine() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WriteStringResult run() {
        try {
            if(this.ifDiffers){
                String s = $.sys.readString(this.getFullPath(), "");

                if(this.text.equals(s)) {
                    return new WriteStringResult(CommandLineResult.OK, false);
                }
            }

            final File tempFile = File.createTempFile("bear", "upload");
            FileUtils.writeStringToFile(tempFile, this.text, IOUtils.UTF8.name());
            String remoteTempPath = tempFile.getName();

            $.sys.upload(remoteTempPath, tempFile);

            tempFile.delete();

            CommandLineResult move = $.sys.move(remoteTempPath).to(this.path)
                .withPermissions(this.permissions)
                .withUser(this.user)
                .sudo(this.sudo)
                .callback(this.callback)
                .run();

            return new WriteStringResult(move, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
