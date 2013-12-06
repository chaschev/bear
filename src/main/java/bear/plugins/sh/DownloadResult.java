package bear.plugins.sh;

import bear.session.Result;
import bear.task.TaskResult;

import java.io.File;
import java.util.List;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class DownloadResult extends TaskResult {
    List<File> files;

    public DownloadResult(List<File> files) {
        super(Result.OK);
        this.files = files;
    }

    public DownloadResult(Throwable e) {
        super(e);
    }
}
