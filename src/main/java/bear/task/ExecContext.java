package bear.task;

import bear.context.HavingContext;
import bear.core.SessionContext;
import org.joda.time.DateTime;

public abstract class ExecContext<SELF> extends HavingContext<SELF, SessionContext> {
    public final ExecContext parent;

    DateTime startedAt = new DateTime();
    DateTime finishedAt;


    ExecContext(SessionContext $, ExecContext parent) {
        super($);
        this.parent = parent;
    }

    public void onEnd(TaskResult result) {
        finishedAt = new DateTime();
    }

    public DateTime getStartedAt() {
        return startedAt;
    }

    public DateTime getFinishedAt() {
        return finishedAt;
    }

    public abstract boolean visit(TaskExecutionContext.ExecutionVisitor visitor);


}
