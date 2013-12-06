package bear.plugins.misc;

import bear.vcs.BranchInfo;
import bear.vcs.VcsLogInfo;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class PendingRelease extends Release{
    final Releases releases;

    PendingRelease(VcsLogInfo log, BranchInfo branchInfo, String path, Releases releases) {
        super(log, branchInfo, path, "pending");
        this.releases = releases;
    }

    public Release activate(){
        return releases.activatePending(this);
    }
}
