package bear.plugins.misc;

import bear.task.TaskResult;
import bear.vcs.BranchInfo;
import bear.vcs.VcsLogInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Optional;
import org.apache.commons.io.FilenameUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.google.common.base.Optional.absent;
import static com.google.common.base.Optional.fromNullable;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class Release {
    public String path;

    @Nonnull
    protected Optional<VcsLogInfo> log;

    @Nonnull
    protected Optional<BranchInfo> branchInfo;

    public String status;

    public Release() {
    }

    public Release(Optional<VcsLogInfo> log, Optional<BranchInfo> branchInfo, String path, String status) {
        this.log = TaskResult.okOrAbsent(log);
        this.branchInfo = TaskResult.okOrAbsent(branchInfo);
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

    public void setLog(VcsLogInfo log) {
        this.log = Optional.fromNullable(log);
    }

    public void setBranchInfo(BranchInfo branchInfo) {
        this.branchInfo = Optional.fromNullable(branchInfo);
    }

    @Nullable
    public VcsLogInfo getLog() {
        return log.orNull();
    }

    @Nullable
    public BranchInfo getBranchInfo() {
        return branchInfo.orNull();
    }

    @JsonIgnore
    public String getLastAuthor() {
        Optional<String> optional = absent();

        if(log.isPresent()){
            optional = fromNullable(log.get().lastAuthor());
        }

        if(branchInfo.isPresent()){
            optional = optional.or(fromNullable(branchInfo.get().author));
        }

        return  optional.or("<no entry>");
    }
}
