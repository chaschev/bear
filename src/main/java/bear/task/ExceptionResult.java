package bear.task;

import bear.session.Result;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class ExceptionResult extends TaskResult {
    public final Throwable e;
    public ExceptionResult(Throwable e) {
        super(Result.ERROR);
        this.e = e;
    }
}
