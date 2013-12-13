package bear.plugins.sh;

import bear.session.Result;
import bear.task.TaskResult;

/**
* @author Andrey Chaschev chaschev@gmail.com
*/
public class WriteStringResult extends TaskResult {
    boolean wrote;

    public WriteStringResult(Result result, boolean wrote) {
        super(result);
        this.wrote = wrote;
    }

    public WriteStringResult(Throwable e) {
        super(e);
    }
}
