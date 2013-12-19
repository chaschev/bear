package bear.plugins.sh;

import bear.core.SessionContext;
import bear.core.except.ValidationException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class RmBuilder extends CommandBuilder<RmBuilder> {
    protected final String[] paths;
    protected boolean force = true;

    public RmBuilder(SessionContext $, String... paths) {
        super($);
        this.paths = paths;
    }

    public RmBuilder force(boolean force) {
        this.force = force;
        return this;
    }

    public static RmBuilder newRm(SessionContext $, String... paths){
        return new RmBuilder($, paths);
    }

    @Override
    public void validate() {
        super.validate();

        for (String path : paths) {
            if(cd.isPresent() && !cd.get().equals(".")){
                path = FilenameUtils.normalize(cd.get() + "/" + path, true);
            }

            path = FilenameUtils.normalize(path, true);

            int dirLevel = StringUtils.split(path, '/').length;

            if(dirLevel <= 2) {
                throw new ValidationException(String.format("won't delete a directory on the second level or higher: %s, dir: %s", dirLevel, path));
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        RmBuilder rmInput = (RmBuilder) o;

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
    public CommandLine asLine() {
        super.asLine();

        return newLine($).addRaw("rm").a(recursive ? (force ? "-rf" : "-r") : "-f" ).a(paths);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RmInput{");
        sb.append("paths=").append(Arrays.toString(paths));
        sb.append(", sudo=").append(sudo);
        if(cd.isPresent()) sb.append(", cd=").append(cd.get());
        sb.append(", recursive=").append(recursive);
        sb.append(", force=").append(force);
        sb.append('}');
        return sb.toString();
    }
}
