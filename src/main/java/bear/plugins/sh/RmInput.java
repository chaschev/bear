package bear.plugins.sh;

import bear.core.Bear;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class RmInput extends CommandInput<RmInput> {
    protected final String[] paths;
    protected boolean recursive = true;
    protected boolean force = true;

    public RmInput(String... paths) {
        this.paths = paths;
    }

   public RmInput nonRecursive() {
        recursive = false;
        return this;
    }

    public RmInput force(boolean force) {
        this.force = force;
        return this;
    }

    public static RmInput newRm(String... paths){
        return new RmInput(paths);
    }

    @Override
    public void validate() {
        super.validate();

        for (String path : paths) {
            if(cd != null && !cd.equals(".")){
                path = FilenameUtils.normalize(cd + "/" + path, true);
            }

            path = FilenameUtils.normalize(path, true);

            int dirLevel = StringUtils.split(path, '/').length;

            if(dirLevel <= 2) {
                throw new Bear.ValidationException(String.format("won't delete a directory on the second level or higher: %s, dir: %s", dirLevel, path));
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        RmInput rmInput = (RmInput) o;

        if (force != rmInput.force) return false;
        if (recursive != rmInput.recursive) return false;
        if (!Arrays.equals(paths, rmInput.paths)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (recursive ? 1 : 0);
        result = 31 * result + (force ? 1 : 0);
        result = 31 * result + Arrays.hashCode(paths);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RmInput{");
        sb.append("paths=").append(Arrays.toString(paths));
        sb.append(", sudo=").append(sudo);
        sb.append(", cd=").append(cd);
        sb.append(", recursive=").append(recursive);
        sb.append(", force=").append(force);
        sb.append('}');
        return sb.toString();
    }
}
