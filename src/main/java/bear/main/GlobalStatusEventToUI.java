package bear.main;

import bear.core.CompositeTaskRunContext;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class GlobalStatusEventToUI extends EventToUI {
    public CompositeTaskRunContext.Stats stats;

    protected GlobalStatusEventToUI(CompositeTaskRunContext.Stats stats) {
        super("status", "global");

        this.stats = stats;
    }
}
