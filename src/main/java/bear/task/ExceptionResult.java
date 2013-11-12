package bear.task;

import bear.session.Result;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class ExceptionResult extends TaskResult {
    public final Exception e;
    public ExceptionResult(Exception e) {
        super(Result.ERROR);
        this.e = e;
    }
}
