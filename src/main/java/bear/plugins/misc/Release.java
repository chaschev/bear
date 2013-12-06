package bear.plugins.misc;

import bear.vcs.BranchInfo;
import bear.vcs.VcsLogInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.io.FilenameUtils;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class Release {
    public String path;
    public VcsLogInfo log;
    public BranchInfo branchInfo;
    public String status;

    public Release() {
    }

    public Release(VcsLogInfo log, BranchInfo branchInfo, String path, String status) {
        this.log = log;
        this.branchInfo = branchInfo;
        this.path = path;
        this.status = status;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Release{");
        sb.append("path='").append(path).append('\'');
        sb.append(", branchInfo=").append(branchInfo);
        sb.append(", status='").append(status).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public String name() {
        return FilenameUtils.getName(path);
    }

    @JsonIgnore
    public boolean isActive() {
        return "active".equals(status);
    }
}
